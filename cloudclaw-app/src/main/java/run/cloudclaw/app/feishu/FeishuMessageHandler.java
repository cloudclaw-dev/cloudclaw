package run.cloudclaw.app.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import run.cloudclaw.agent.engine.ChatEngine;
import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.auth.repository.ChannelBindingRepository;
import run.cloudclaw.auth.repository.FeishuConversationRepository;
import run.cloudclaw.auth.repository.UserRepository;
import run.cloudclaw.common.model.ChannelBinding;
import run.cloudclaw.common.model.ChannelConfig;
import run.cloudclaw.common.model.FeishuConversation;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.common.model.User;
import run.cloudclaw.session.service.SessionService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Shared Feishu message processing logic for both HTTP callback and WebSocket long-connection modes.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Idempotent deduplication via message_id (Caffeine cache, 10 min TTL)</li>
 *   <li>Thread pool isolation (feishuExecutor, fixed 8 daemon threads)</li>
 *   <li>User auto-registration and session management</li>
 *   <li>ChatEngine.chatAsync with completion callback (no DB polling)</li>
 *   <li>Feishu HTTP API reply (app_access_token, send message)</li>
 * </ul>
 */
@Slf4j
@Component
public class FeishuMessageHandler {

    private static final String CHANNEL_TYPE = "feishu";
    private static final int MAX_REPLY_LENGTH = 4000;

    private final ChatEngine chatEngine;
    private final AgentConfigService agentConfigService;
    private final SessionService sessionService;
    private final ChannelConfigService channelConfigService;
    private final ChannelBindingRepository channelBindingRepository;
    private final FeishuConversationRepository feishuConversationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Feishu message processing thread pool (isolated from ChatEngine) */
    private final ExecutorService feishuExecutor = Executors.newFixedThreadPool(
            8, r -> {
                Thread t = new Thread(r, "feishu-msg-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            });

    /** Dedup cache: message_id -> true, 10 min TTL, max 10000 entries */
    private final Cache<String, Boolean> processedIds = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    public FeishuMessageHandler(ChatEngine chatEngine,
                                 AgentConfigService agentConfigService,
                                 SessionService sessionService,
                                 ChannelConfigService channelConfigService,
                                 ChannelBindingRepository channelBindingRepository,
                                 FeishuConversationRepository feishuConversationRepository,
                                 UserRepository userRepository,
                                 PasswordEncoder passwordEncoder) {
        this.chatEngine = chatEngine;
        this.agentConfigService = agentConfigService;
        this.sessionService = sessionService;
        this.channelConfigService = channelConfigService;
        this.channelBindingRepository = channelBindingRepository;
        this.feishuConversationRepository = feishuConversationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ===== Public entry points =====

    /**
     * Handle a Feishu message from the HTTP callback endpoint.
     * Extracts message fields from the raw JSON event payload.
     *
     * @param config      the channel config
     * @param openId      sender open_id
     * @param chatId      Feishu chat_id
     * @param chatType    "p2p" or "group"
     * @param msgId       Feishu message_id for deduplication
     * @param userMessage extracted text content
     */
    public void handleMessageFromCallback(ChannelConfig config, String openId, String chatId,
                                           String chatType, String msgId, String userMessage) {
        if (msgId != null && processedIds.asMap().putIfAbsent(msgId, true) != null) {
            log.info("Duplicate Feishu message ignored (callback): {}", msgId);
            return;
        }
        feishuExecutor.submit(() -> processMessage(config, openId, chatId, chatType, msgId, userMessage));
    }

    /**
     * Handle a Feishu message from the WebSocket long-connection SDK event.
     *
     * @param config      the channel config
     * @param openId      sender open_id
     * @param chatId      Feishu chat_id
     * @param chatType    "p2p" or "group"
     * @param msgId       Feishu message_id for deduplication
     * @param userMessage extracted text content
     */
    public void handleMessageFromWs(ChannelConfig config, String openId, String chatId,
                                     String chatType, String msgId, String userMessage) {
        if (msgId != null && processedIds.asMap().putIfAbsent(msgId, true) != null) {
            log.info("Duplicate Feishu message ignored (ws): {}", msgId);
            return;
        }
        feishuExecutor.submit(() -> processMessage(config, openId, chatId, chatType, msgId, userMessage));
    }

    // ===== Core processing =====

    /**
     * Core message processing: user registration, session management, ChatEngine callback.
     * Runs on feishuExecutor. Uses callback mechanism (no DB polling).
     */
    private void processMessage(ChannelConfig config, String openId, String chatId,
                                 String chatType, String msgId, String userMessage) {
        // React to the user's message first to acknowledge receipt
        if (msgId != null) {
            try {
                reactToMessage(config, msgId, "Typing");
            } catch (Exception e) {
                log.warn("Failed to react to Feishu message {}: {}", msgId, e.getMessage());
            }
        }

        try {
            String userId = findOrCreateUser(config, openId);
            String sessionId = findOrCreateSession(config, chatId, chatType, openId, userId);

            // Load agent info for card footer
            String agentName = "Agent";
            String modelName = "default";
            try {
                Session sessionInfo = sessionService.getSession(userId, sessionId);
                AgentConfig agentCfg = agentConfigService.getAgentConfig(sessionInfo.getAgentId().toString());
                if (agentCfg.getName() != null) agentName = agentCfg.getName();
                if (agentCfg.getModelId() != null) modelName = agentCfg.getModelId();
            } catch (Exception e) {
                log.warn("Failed to load agent info for card footer: {}", e.getMessage());
            }

            // Use streaming chat for real-time card updates
            StreamReplyContext ctx = new StreamReplyContext(config, chatId, agentName, modelName);
            ctx.start();

            chatEngine.chat(userId, sessionId, userMessage)
                    .doOnNext(chunk -> {
                        if ("text".equals(chunk.getType()) && chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                            ctx.appendContent(chunk.getContent());
                        } else if ("tool_call".equals(chunk.getType()) && chunk.getToolName() != null) {
                            ctx.setToolIndicator(chunk.getToolName());
                        } else if (chunk.isDone()) {
                            ctx.markDone();
                        }
                    })
                    .doOnError(e -> {
                        log.error("Feishu stream chat error", e);
                        ctx.appendContent("\n\n⚠️ 消息处理出错: " + e.getMessage());
                        ctx.markDone();
                    })
                    .doOnComplete(() -> {
                        ctx.markDone();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("Error processing Feishu message", e);
            try {
                sendFeishuReply(config, chatId, "抱歉，处理消息时出错。");
            } catch (Exception ignored) {
            }
        }
    }

    // ===== User registration =====

    private String findOrCreateUser(ChannelConfig config, String openId) {
        ChannelBinding binding = channelBindingRepository
                .findByChannelTypeAndChannelUserId(CHANNEL_TYPE, openId)
                .orElse(null);
        if (binding != null) return binding.getUserId();

        log.info("Auto-registering Feishu user: openId={}", openId);
        String appAccessToken = getAppAccessToken(config);
        FeishuUserInfo userInfo = getFeishuUserInfo(appAccessToken, openId);

        String username = "feishu_" + openId.substring(0, Math.min(8, openId.length()));
        int suffix = 1;
        String baseUsername = username;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + "_" + suffix++;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEmail(username + "@feishu.local");
        user.setDisplayName(userInfo.name());
        user.setAvatarUrl(userInfo.avatarUrl());
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);
        user = userRepository.save(user);

        ChannelBinding newBinding = new ChannelBinding();
        newBinding.setUserId(user.getId().toString());
        newBinding.setChannelType(CHANNEL_TYPE);
        newBinding.setChannelUserId(openId);
        try {
            newBinding.setChannelData(objectMapper.writeValueAsString(Map.of(
                    "name", userInfo.name() != null ? userInfo.name() : "",
                    "avatarUrl", userInfo.avatarUrl() != null ? userInfo.avatarUrl() : "",
                    "openId", openId)));
        } catch (Exception ignored) {
        }
        channelBindingRepository.save(newBinding);

        log.info("Auto-registered Feishu user: openId={}, username={}", openId, username);
        return user.getId().toString();
    }

    // ===== Session management =====

    private String findOrCreateSession(ChannelConfig config, String chatId,
                                        String chatType, String openId, String userId) {
        FeishuConversation conv = feishuConversationRepository
                .findByChannelConfigIdAndFeishuChatIdAndFeishuUserId(config.getId(), chatId, openId)
                .orElse(null);

        if (conv != null && conv.getSessionId() != null) {
            try {
                sessionService.getSession(userId, conv.getSessionId());
                return conv.getSessionId();
            } catch (Exception e) {
                log.warn("Session {} not found, creating new one", conv.getSessionId());
            }
        }

        String agentId = config.getAgentId();
        if (agentId == null) throw new RuntimeException("No agent configured for this channel");

        run.cloudclaw.common.model.Session session = sessionService.createSession(
                userId, agentId,
                "Feishu: " + chatType);

        if (conv == null) {
            conv = new FeishuConversation();
            conv.setChannelConfigId(config.getId());
            conv.setFeishuChatId(chatId);
            conv.setFeishuChatType(chatType);
            conv.setFeishuUserId(openId);
        }
        conv.setSessionId(session.getId());
        feishuConversationRepository.save(conv);

        log.info("Created session {} for feishu chatId={}, openId={}", session.getId(), chatId, openId);
        return session.getId().toString();
    }

    // ===== Feishu API helpers =====

    /**
     * Extract text content from Feishu message content JSON.
     */
    public String extractText(String content, String msgType) {
        if ("text".equals(msgType)) {
            try {
                return objectMapper.readTree(content).path("text").asText();
            } catch (Exception e) {
                return content;
            }
        }
        return null;
    }

    private String getAppAccessToken(ChannelConfig config) {
        String appSecret = channelConfigService.getDecryptedSecret(config);
        try {
            String body = "{\"app_id\":\"" + config.getAppId() + "\",\"app_secret\":\"" + appSecret + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0) {
                throw new RuntimeException("Failed to get app_access_token: " + json.path("msg").asText());
            }
            return json.path("app_access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Feishu API error: " + e.getMessage(), e);
        }
    }

    private FeishuUserInfo getFeishuUserInfo(String appAccessToken, String openId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/contact/v3/users/" + openId + "?user_id_type=open_id"))
                    .header("Authorization", "Bearer " + appAccessToken)
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0) {
                return new FeishuUserInfo(openId, "Feishu User " + openId.substring(0, 4), null);
            }
            JsonNode data = json.path("data").path("user");
            return new FeishuUserInfo(openId,
                    data.path("name").asText("Feishu User"),
                    data.path("avatar").path("avatar_72").asText(""));
        } catch (Exception e) {
            return new FeishuUserInfo(openId, "Feishu User", null);
        }
    }

    /**
     * Send a reply to a Feishu chat. Splits messages exceeding 4000 chars.
     */
    public void sendFeishuReply(ChannelConfig config, String chatId, String text) {
        try {
            String token = getAppAccessToken(config);
            if (text.length() > MAX_REPLY_LENGTH) {
                int idx = 0;
                while (idx < text.length()) {
                    String chunk = text.substring(idx, Math.min(idx + MAX_REPLY_LENGTH, text.length()));
                    sendSingleMessage(token, chatId, chunk);
                    idx += MAX_REPLY_LENGTH;
                }
            } else {
                sendSingleMessage(token, chatId, text);
            }
        } catch (Exception e) {
            log.error("Failed to send Feishu reply to chatId={}", chatId, e);
        }
    }

    private void sendSingleMessage(String token, String chatId, String text) throws Exception {
        // Use interactive card with markdown element for rich formatting
        com.fasterxml.jackson.databind.node.ObjectNode cardNode = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode config = cardNode.putObject("config");
        config.put("wide_screen", true);

        com.fasterxml.jackson.databind.node.ArrayNode elements = cardNode.putArray("elements");
        com.fasterxml.jackson.databind.node.ObjectNode mdElement = elements.addObject();
        mdElement.put("tag", "markdown");
        mdElement.put("content", text);

        String contentJson = objectMapper.writeValueAsString(cardNode);

        com.fasterxml.jackson.databind.node.ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("receive_id", chatId);
        bodyNode.put("msg_type", "interactive");
        bodyNode.put("content", contentJson);
        String body = objectMapper.writeValueAsString(bodyNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        if (json.path("code").asInt(-1) != 0) {
            log.error("Feishu send message failed: {}", response.body());
        }
    }

    /**
     * React (add emoji) to a Feishu message to acknowledge receipt.
     *
     * @param config    channel config for API credentials
     * @param messageId Feishu message_id to react to
     * @param emojiType Feishu emoji type, e.g. "Typing", "HEART", "YES"
     */
    private void reactToMessage(ChannelConfig config, String messageId, String emojiType) {
        try {
            String token = getAppAccessToken(config);
            com.fasterxml.jackson.databind.node.ObjectNode bodyNode = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode reactionType = bodyNode.putObject("reaction_type");
            reactionType.put("emoji_type", emojiType);
            String body = objectMapper.writeValueAsString(bodyNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId + "/reactions"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0) {
                log.warn("Feishu react failed: {}", response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to react to Feishu message: {}", e.getMessage());
        }
    }

    /**
     * Streaming reply context: sends an initial card, then patches it as content arrives.
     * Throttles updates to avoid Feishu API rate limits (max ~1 patch per 1.5s).
     */
    private class StreamReplyContext {
        private final ChannelConfig config;
        private final String chatId;
        private final String agentName;
        private final String modelName;
        private final StringBuilder content = new StringBuilder();
        private volatile String feishuMessageId; // message_id returned by Feishu after initial send
        private volatile boolean initialized = false;
        private volatile boolean done = false;
        private volatile String toolIndicator = null;
        private volatile long lastPatchTime = 0;
        private volatile boolean dirty = false;
        private final java.util.concurrent.ScheduledExecutorService scheduler =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "feishu-stream-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                });

        StreamReplyContext(ChannelConfig config, String chatId, String agentName, String modelName) {
            this.config = config;
            this.chatId = chatId;
            this.agentName = agentName;
            this.modelName = modelName;
        }

        void start() {
            // Send initial "thinking" card
            try {
                feishuMessageId = sendCardMessage("💭 思考中...");
                initialized = true;
                // Start periodic flush
                scheduler.scheduleAtFixedRate(this::flushIfNeeded, 500, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Failed to send initial Feishu card", e);
            }
        }

        synchronized void appendContent(String text) {
            content.append(text);
            dirty = true;
        }

        synchronized void setToolIndicator(String toolName) {
            toolIndicator = toolName;
            dirty = true;
        }

        void markDone() {
            done = true;
        }

        private synchronized void flushIfNeeded() {
            if (!initialized || feishuMessageId == null) return;
            long now = System.currentTimeMillis();
            boolean shouldFlush = dirty && (done || (now - lastPatchTime) >= 1500);
            if (!shouldFlush) {
                if (done && !dirty) {
                    scheduler.shutdown();
                }
                return;
            }
            dirty = false;
            lastPatchTime = now;
            String text = content.toString();
            if (text.isEmpty()) {
                // Still thinking or calling tools
                String indicator = toolIndicator != null ? "🔧 正在使用工具: " + toolIndicator + "..." : "💭 思考中...";
                try {
                    patchCardMessage(feishuMessageId, indicator);
                } catch (Exception e) {
                    log.warn("Failed to patch intermediate Feishu card: {}", e.getMessage());
                }
            } else {
                String displayText = text;
                if (!done) {
                    displayText = text + " ▌"; // cursor indicator
                }
                try {
                    patchCardMessage(feishuMessageId, displayText);
                } catch (Exception e) {
                    log.warn("Failed to patch intermediate Feishu card: {}", e.getMessage());
                }
            }
            if (done) {
                scheduler.shutdown();
            }
        }

        private String sendCardMessage(String text) throws Exception {
            String token = getAppAccessToken(config);
            String cardJson = buildCardJson(text);
            com.fasterxml.jackson.databind.node.ObjectNode bodyNode = objectMapper.createObjectNode();
            bodyNode.put("receive_id", chatId);
            bodyNode.put("msg_type", "interactive");
            bodyNode.put("content", cardJson);
            String body = objectMapper.writeValueAsString(bodyNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0) {
                log.error("Feishu send card failed: {}", response.body());
                return null;
            }
            return json.path("data").path("message_id").asText();
        }

        private void patchCardMessage(String messageId, String text) throws Exception {
            String token = getAppAccessToken(config);
            String cardJson = buildCardJson(text);

            com.fasterxml.jackson.databind.node.ObjectNode bodyNode = objectMapper.createObjectNode();
            bodyNode.put("msg_type", "interactive");
            bodyNode.put("content", cardJson);
            String body = objectMapper.writeValueAsString(bodyNode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId + "?receive_id_type=chat_id"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0) {
                log.warn("Feishu patch card failed: {}", response.body());
            }
        }

        private String buildCardJson(String text) {
            com.fasterxml.jackson.databind.node.ObjectNode cardNode = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode config = cardNode.putObject("config");
            config.put("wide_screen", true);

            com.fasterxml.jackson.databind.node.ArrayNode elements = cardNode.putArray("elements");
            com.fasterxml.jackson.databind.node.ObjectNode mdElement = elements.addObject();
            mdElement.put("tag", "markdown");
            mdElement.put("content", text);

            // Add footer with agent and model info
            com.fasterxml.jackson.databind.node.ObjectNode hr = elements.addObject();
            hr.put("tag", "hr");

            com.fasterxml.jackson.databind.node.ObjectNode note = elements.addObject();
            note.put("tag", "note");
            com.fasterxml.jackson.databind.node.ArrayNode noteElements = note.putArray("elements");
            com.fasterxml.jackson.databind.node.ObjectNode noteText = noteElements.addObject();
            noteText.put("tag", "plain_text");
            noteText.put("content", "\ud83e\udd16 " + agentName + "  \u00b7  \ud83e\udde0 " + modelName);

            try {
                return objectMapper.writeValueAsString(cardNode);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    private record FeishuUserInfo(String openId, String name, String avatarUrl) {
    }
}
