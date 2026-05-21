package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.agent.tools.SkillTools;
import run.cloudclaw.agent.tools.MemoryTools;
import run.cloudclaw.sandbox.tool.SandboxExecuteTool;
import run.cloudclaw.sandbox.tool.SandboxFileTool;
import run.cloudclaw.sandbox.tool.SandboxInfoTool;
import run.cloudclaw.sandbox.core.SandboxBackend;
import run.cloudclaw.sandbox.core.SandboxContext;
import run.cloudclaw.sandbox.core.SandboxMode;
import run.cloudclaw.agent.prompt.PromptAssembler;
import run.cloudclaw.common.dto.AsyncChatResult;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.model.McpServer;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.llm.service.LlmRouteService;
import run.cloudclaw.llm.service.LlmUsageService;
import run.cloudclaw.mcp.repository.McpServerRepository;
import run.cloudclaw.agent.engine.SessionCompressor;
import run.cloudclaw.session.service.SessionService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import run.cloudclaw.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Core chat engine that orchestrates the full conversation flow.
 *
 * <p>Uses Spring AI's {@link ToolCallAdvisor} for automatic tool calling
 * in both streaming and non-streaming modes.</p>
 */
@Service
@Slf4j
public class ChatEngine {

    private final LlmRouteService llmRouteService;
    private final SessionService sessionService;
    private final AgentConfigService agentConfigService;
    private final PromptAssembler promptAssembler;
    private final LlmUsageService llmUsageService;
    private final SkillService skillService;
    private final SkillTools skillTools;
    private final McpServerRepository mcpServerRepository;
    private final MemoryTools memoryTools;
    private final SessionCompressor sessionCompressor;
    private final ContextCompressor contextCompressor;
    private final Executor chatExecutor;
    private final run.cloudclaw.agent.prompt.PromptLogService promptLogService;
    private final run.cloudclaw.memory.injector.MemoryInjector memoryInjector;
    private final SandboxExecuteTool sandboxExecuteTool;
    private final SandboxFileTool sandboxFileTool;
    private final SandboxInfoTool sandboxInfoTool;

    public ChatEngine(LlmRouteService llmRouteService,
                      SessionService sessionService,
                      AgentConfigService agentConfigService,
                      PromptAssembler promptAssembler,
                      LlmUsageService llmUsageService,
                      SkillService skillService,
                      SkillTools skillTools,
                      McpServerRepository mcpServerRepository,
                      MemoryTools memoryTools,
                      SessionCompressor sessionCompressor,
                      ContextCompressor contextCompressor,
                      @Qualifier("chatExecutor") Executor chatExecutor,
                      run.cloudclaw.agent.prompt.PromptLogService promptLogService,
                      run.cloudclaw.memory.injector.MemoryInjector memoryInjector,
                      SandboxExecuteTool sandboxExecuteTool,
                      SandboxFileTool sandboxFileTool,
                      SandboxInfoTool sandboxInfoTool) {
        this.llmRouteService = llmRouteService;
        this.sessionService = sessionService;
        this.agentConfigService = agentConfigService;
        this.promptAssembler = promptAssembler;
        this.llmUsageService = llmUsageService;
        this.skillService = skillService;
        this.skillTools = skillTools;
        this.mcpServerRepository = mcpServerRepository;
        this.memoryTools = memoryTools;
        this.sessionCompressor = sessionCompressor;
        this.contextCompressor = contextCompressor;
        this.chatExecutor = chatExecutor;
        this.promptLogService = promptLogService;
        this.memoryInjector = memoryInjector;
        this.sandboxExecuteTool = sandboxExecuteTool;
        this.sandboxFileTool = sandboxFileTool;
        this.sandboxInfoTool = sandboxInfoTool;
    }

    /**
     * Execute a chat request and return the response as a streaming Flux.
     * Uses ToolCallAdvisor for automatic tool calling in stream mode.
     */
    public Flux<ChatChunk> chat(String userId, String sessionId, String userMessage) {
        log.info("Chat request: userId={}, sessionId={}, messageLength={}", userId, sessionId, userMessage.length());

        // 1. Load session context (with summary compression)
        List<Message> rawHistory = sessionCompressor.loadContextWithSummary(sessionId);

        // 2. Get session to find agentId
        Session session = sessionService.getSession(userId, sessionId);

        // 3. Load agent config
        AgentConfig config = agentConfigService.getAgentConfig(session.getAgentId().toString());

        // 4. Assemble system prompt (base prompt + skills + memory)
        String systemPrompt = promptAssembler.assembleSystemPrompt(
                config, userId, sessionId, userMessage);

        // 4.5 Dynamic context compression: trim history if exceeding token budget
        int contextWindow = config.getContextWindow() != null ? config.getContextWindow() : 128000;
        List<Message> history = contextCompressor.compress(rawHistory, systemPrompt, userMessage, contextWindow, config.getContextUsageThreshold());

        // 5. Save user message
        Message userMsg = new Message();
        userMsg.setSessionId(UUID.fromString(sessionId));
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        sessionService.saveMessage(userMsg);

        // 6. Resolve ChatClient
        ChatClient chatClient = llmRouteService.getChatClient(config.getModelId());
        log.info("Resolved modelId={} for agent={}", config.getModelId(), config.getAgentId());

        // 6.5 Set memory tools context for this request
        String agentIdStr = session.getAgentId() != null ? session.getAgentId().toString() : null;
        MemoryTools.setContext(userId, agentIdStr, sessionId, config.getMemoryProfileMaxTokens(), config.getMemoryTaskMaxTokens());

        // 6.6 Set sandbox tool context if sandbox is enabled
        if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
            SandboxMode smode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;
            SandboxBackend sbackend = config.getSandboxBackend() != null ? SandboxBackend.valueOf(config.getSandboxBackend()) : SandboxBackend.LOCAL;
            String sproviderId = config.getSandboxProviderId();
            SandboxContext.set(sessionId, agentIdStr, smode, sbackend, sproviderId);
        }

        // 7. Build tool callbacks from providers that have @Tool methods
        List<McpSyncClient> createdMcpClients = new ArrayList<>();
        List<ToolCallback> toolCallbacks = resolveToolCallbacks(config, createdMcpClients);

        // 8. Build Spring AI messages
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (Message msg : history) {
            switch (msg.getRole()) {
                case "user" -> aiMessages.add(new UserMessage(msg.getContent()));
                case "assistant" -> aiMessages.add(new AssistantMessage(msg.getContent()));
                case "system", "summary" -> aiMessages.add(new SystemMessage(msg.getContent()));
                default -> log.warn("Unknown message role: {}, skipping", msg.getRole());
            }
        }

        String effectiveSystemPrompt = systemPrompt;

        // 8.5 Append memory tool usage guide if memory tools are enabled
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            String memoryGuide = "\n\n## Memory\n\n" +
                    "You have persistent memory via 2 tools. Use them proactively \u2014 don't wait to be asked.\n\n" +
                    "Core principle: Save only facts that will still matter in future sessions.\n" +
                    "The most valuable memory prevents the user from having to repeat themselves.\n\n" +
                    "Two targets:\n" +
                    "- memory_profile: Who the user IS \u2014 name, role, preferences, habits, corrections.\n" +
                    "  Persists across all sessions. 1000 token limit.\n" +
                    "- memory_session: Current task context \u2014 goals, progress, agreements, constraints.\n" +
                    "  This session only. 2000 token limit.\n\n" +
                    "WHEN TO SAVE (proactive):\n" +
                    "- User corrects you or says 'remember this' / 'don't do that again'\n" +
                    "- User shares a preference, habit, or personal detail\n" +
                    "- User mentions their name, role, timezone, or communication style\n" +
                    "Priority: User corrections > preferences > personal facts > communication style.\n\n" +
                    "DO NOT save: common knowledge, completed-work logs, temporary TODO state,\n" +
                    "or anything that will be stale in 7 days.\n\n" +
                    "HOW TO WRITE \u2014 declarative facts, not instructions:\n" +
                    "\u2713 'User prefers concise responses'  \u2717 'Always respond concisely'\n\n" +
                    "ACTIONS:\n" +
                    "- memory_profile: read_all | add | replace | remove\n" +
                    "- memory_session: read_all | add | replace | remove";
            effectiveSystemPrompt = systemPrompt + memoryGuide;
        }
        // 9. Build ChatClient request";
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .system(effectiveSystemPrompt)
                .messages(aiMessages)
                .user(userMessage);

        log.info("System prompt length: {}, contains memory guide: {}",
                effectiveSystemPrompt.length(), effectiveSystemPrompt.contains("Memory Tools"));

        if (!toolCallbacks.isEmpty()) {
            // Wrap all tool callbacks with truncating decorator (max 3000 chars per result)
            int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
            List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                    .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                    .toList();

            ToolCallingManager toolCallingManager = new LimitedToolCallingManager(
                    DefaultToolCallingManager.builder().build(),
                    config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50
            );

            ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .streamToolCallResponses(true)
                    .build();

            requestSpec.toolCallbacks(truncatedCallbacks)
                    .advisors(toolCallAdvisor);

            log.info("ChatClient configured with {} tool callbacks (truncated at {} chars) and ToolCallAdvisor (streaming)",
                    truncatedCallbacks.size(), maxToolResultChars);
        }

        // 10. Use Sinks.Many to decouple LLM execution from SSE subscription.
        //     If the frontend disconnects, the LLM continues running in background
        //     and saves the result to DB. User sees it when they come back.
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);
        StringBuilder fullResponse = new StringBuilder();

        // Log the prompts before starting chat thread
        promptLogService.logAsync(sessionId, agentIdStr, userId,
                config.getModelId(), "user", userMessage,
                estimateTokens(userMessage), null, null);
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
            promptLogService.logAsync(sessionId, agentIdStr, userId,
                    config.getModelId(), "system", effectiveSystemPrompt,
                    estimateTokens(effectiveSystemPrompt), null, null);
        }

        // Run LLM in a separate thread so cancellation of the SSE flux
        // does NOT cancel the LLM call.
        Thread chatThread = new Thread(() -> {
            // Bind sandbox context to this thread (SandboxContext uses ConcurrentHashMap + ThreadLocal)
            SandboxContext.bindToThread(sessionId);
            long startTime = System.currentTimeMillis();
            try {
                requestSpec.stream()
                        .chatResponse()
                        .map(chatResponse -> {
                            if (chatResponse.getResult() == null
                                    || chatResponse.getResult().getOutput() == null) {
                                return ChatChunk.text("");
                            }

                            var output = chatResponse.getResult().getOutput();

                            // Tool call chunk
                            if (output.hasToolCalls()) {
                                var toolCalls = output.getToolCalls();
                                if (!toolCalls.isEmpty()) {
                                    var tc = toolCalls.get(0);
                                    String toolName = tc.name();
                                    String args = tc.arguments() != null ? tc.arguments() : "";
                                    log.debug("Tool call: name={}, args={}", toolName, args);
                                    return ChatChunk.toolCall(toolName, args);
                                }
                            }

                            // Normal text chunk
                            String content = output.getText();
                            if (content != null) {
                                fullResponse.append(content);
                            }
                            return ChatChunk.text(content != null ? content : "");
                        })
                        .doOnNext(chunk -> sink.tryEmitNext(chunk))
                        .doOnError(e -> {
                            log.error("Chat stream error for session {}: {}", sessionId, e.getMessage());
                            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
                                log.error("DeepSeek response body: {}", webEx.getResponseBodyAsString());
                            }
                            log.error("Full exception", e);
                            // Save partial response on error
                            if (fullResponse.length() > 0) {
                                try {
                                    Message partial = new Message();
                                    partial.setSessionId(UUID.fromString(sessionId));
                                    partial.setRole("assistant");
                                    partial.setContent(fullResponse + "\n\n*[Response was interrupted]*");
                                    sessionService.saveMessage(partial);
                                } catch (Exception saveErr) {
                                    log.error("Failed to save partial response: {}", saveErr.getMessage());
                                }
                            }
                            sink.tryEmitError(e);
                        })
                        .doOnComplete(() -> {
                            // Save assistant message
                            String responseText = fullResponse.toString();
                            if (!responseText.isBlank()) {
                                Message assistantMsg = new Message();
                                assistantMsg.setSessionId(UUID.fromString(sessionId));
                                assistantMsg.setRole("assistant");
                                assistantMsg.setContent(responseText);
                                sessionService.saveMessage(assistantMsg);

                                // Log assistant response
                                // (skipped - not recording assistant replies)
                                // promptLogService.logAsync(...)

                                // Fragment extraction removed in v2.0 (LLM manages memory via tools)
                            }

                            // Record token usage
                            try {
                                int approxTokensIn = estimateTokens(userMessage)
                                        + history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum()
                                        + estimateTokens(systemPrompt);
                                int approxTokensOut = estimateTokens(fullResponse.toString());
                                llmUsageService.recordUsage(
                                        config.getModelId(), config.getModelId(), userId,
                                        approxTokensIn, approxTokensOut);
                            } catch (Exception e) {
                                log.warn("Failed to record usage for session {}: {}", sessionId, e.getMessage());
                            }

                            log.info("Chat response saved: sessionId={}, responseLength={}",
                                    sessionId, responseText.length());

                            // Auto-generate title if first message
                            if (history.isEmpty() && session.getTitle() == null && !responseText.isBlank()) {
                                generateTitleAsync(sessionId, userMessage, chatClient);
                            }

                            // Clear memory tools context after conversation
                            MemoryTools.clearContext(userId);
                            SandboxContext.clear();

                            // Trigger session compression check
                            try {
                                sessionCompressor.compressIfNeeded(sessionId, config.getModelId(),
                                        config.getCompressionThreshold(), config.getCompressionKeepRounds());
                            } catch (Exception e) {
                                log.warn("Session compression failed for {}: {}", sessionId, e.getMessage());
                            }

                            // Calculate context stats
                            int sysTokens = estimateTokens(systemPrompt);
                            int histTokens = history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
                            int userTokens = estimateTokens(userMessage);
                            int respTokens = estimateTokens(fullResponse.toString());
                            int totalTokens = sysTokens + histTokens + userTokens + respTokens;
                            int maxContextTokens = config.getContextWindow() != null ? config.getContextWindow() : 128000;
                            int usagePercent = (int) ((long) totalTokens * 100 / maxContextTokens);

                            ChatChunk.ContextStats stats = ChatChunk.ContextStats.builder()
                                    .totalTokens(totalTokens)
                                    .historyMessages(history.size() + 2) // +2 for user msg and assistant reply
                                    .toolCallCount(0) // TODO: track during streaming
                                    .maxTokens(maxContextTokens)
                                    .usagePercent(Math.min(usagePercent, 100))
                                    .systemTokens(sysTokens)
                                    .historyTokens(histTokens)
                                    .memoryTokens(0) // included in systemTokens
                                    .userMessageTokens(userTokens)
                                    .toolResultTokens(0) // TODO: track during streaming
                                    .build();

                            // Signal done
                            sink.tryEmitNext(ChatChunk.builder()
                                    .content("")
                                    .toolCall(false)
                                    .done(true)
                                    .contextStats(stats)
                                    .build());
                            sink.tryEmitComplete();
                        })
                        .subscribe(); // subscribe here to drive the stream
                ;
            } catch (Exception e) {
                log.error("Chat thread error for session {}: {}", sessionId, e.getMessage(), e);
                sink.tryEmitError(e);
            } finally {
                closeMcpClients(createdMcpClients);
                SandboxContext.unbindFromThread();
            }
        });
        chatThread.setName("chat-" + sessionId);
        chatThread.setDaemon(true);
        chatThread.start();

        return sink.asFlux();
    }

    /**
     * Resolve tool callbacks from MemoryTools + SkillTools + MCP Servers.
     * @param createdMcpClients output list to track MCP clients for later cleanup
     */
    private List<ToolCallback> resolveToolCallbacks(AgentConfig config, List<McpSyncClient> createdMcpClients) {
        List<ToolCallback> callbacks = new ArrayList<>();

        // 0. Memory tools (enabled by default)
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            try {
                MethodToolCallbackProvider memoryProvider = MethodToolCallbackProvider.builder()
                        .toolObjects(memoryTools)
                        .build();
                callbacks.addAll(Arrays.asList(memoryProvider.getToolCallbacks()));
                log.info("Resolved {} memory tool callbacks for agent {}",
                        memoryProvider.getToolCallbacks().length, config.getAgentId());
            } catch (Exception e) {
                log.warn("Failed to resolve memory tools: {}", e.getMessage());
            }
        }

        // 1. Skill tools (always available if agent has skills)
        try {
            List<Skill> agentSkills = skillService.getSkillsForAgent(config.getAgentId());
            if (!agentSkills.isEmpty()) {
                MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(skillTools)
                        .build();
                for (ToolCallback tc : provider.getToolCallbacks()) {
                    callbacks.add(tc);
                }
                log.info("Resolved {} skill tool callbacks for agent {}",
                        provider.getToolCallbacks().length, config.getAgentId());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve skill tools: {}", e.getMessage());
        }

        // 2. MCP Server tools
        List<McpSyncClient> mcpClients = new ArrayList<>();
        try {
            // 1.5 Sandbox tools (if enabled for this agent)
            if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
                try {
                    SandboxMode mode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;

                    MethodToolCallbackProvider sandboxProvider = MethodToolCallbackProvider.builder()
                            .toolObjects(sandboxExecuteTool, sandboxInfoTool)
                            .build();
                    callbacks.addAll(Arrays.asList(sandboxProvider.getToolCallbacks()));

                    // File tools only in SESSION mode
                    if (mode == SandboxMode.SESSION) {
                        MethodToolCallbackProvider fileProvider = MethodToolCallbackProvider.builder()
                                .toolObjects(sandboxFileTool)
                                .build();
                        callbacks.addAll(Arrays.asList(fileProvider.getToolCallbacks()));
                    }

                    log.info("Resolved sandbox tool callbacks for agent {} (mode={})", config.getAgentId(), mode);
                } catch (Exception e) {
                    log.warn("Failed to resolve sandbox tools: {}", e.getMessage());
                }
            }

            List<String> mcpServerIds = config.getMcpServerIds();
            if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
                for (String serverId : mcpServerIds) {
                    try {
                        McpServer server = mcpServerRepository.findById(java.util.UUID.fromString(serverId))
                                .orElse(null);
                        if (server == null || !Boolean.TRUE.equals(server.getEnabled())) {
                            log.warn("MCP Server {} not found or disabled", serverId);
                            continue;
                        }

                        // Create MCP client based on transport type
                        McpSyncClient mcpClient = createMcpClient(server);
                        if (mcpClient != null) {
                            mcpClients.add(mcpClient);
                            log.info("Connected to MCP Server: {} ({})", server.getName(), server.getUrl());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to connect to MCP Server {}: {}", serverId, e.getMessage());
                    }
                }

                if (!mcpClients.isEmpty()) {
                    SyncMcpToolCallbackProvider mcpProvider = SyncMcpToolCallbackProvider.builder().mcpClients(mcpClients).build();
                    callbacks.addAll(Arrays.asList(mcpProvider.getToolCallbacks()));
                    log.info("Resolved {} MCP tool callbacks from {} servers for agent {}",
                            mcpProvider.getToolCallbacks().length, mcpClients.size(), config.getAgentId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve MCP tools: {}", e.getMessage());
        }

        log.info("Total {} tool callbacks for agent {}", callbacks.size(), config.getAgentId());
        return callbacks;
    }

    /**
     * Create an MCP SyncClient for the given server configuration.
     */
    private McpSyncClient createMcpClient(McpServer server) {
        String url = server.getUrl();

        if (url == null || url.isBlank()) {
            log.warn("MCP Server {} has no URL configured", server.getName());
            return null;
        }

        // Split URL into baseUri (scheme://host:port) and path (including query string)
        String baseUri;
        String path;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost();
            int port = uri.getPort();
            baseUri = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
            String rawPath = uri.getRawPath();
            String query = uri.getRawQuery();
            path = (rawPath != null ? rawPath : "") + (query != null ? "?" + query : "");
            if (path.isEmpty()) path = "/";
        } catch (java.net.URISyntaxException e) {
            log.warn("Invalid MCP Server URL: {} - {}", url, e.getMessage());
            return null;
        }

        log.debug("MCP URL decomposition: url={}, baseUri={}, path={}", url, baseUri, path);

        String transportType = server.getTransport() != null ? server.getTransport().toLowerCase() : "sse";
        io.modelcontextprotocol.spec.McpClientTransport mcpTransport;

        switch (transportType) {
            case "streamable-http", "streamable_http", "streamable" -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(baseUri)
                        .endpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                log.info("Using Streamable HTTP transport: baseUri={}, endpoint={}", baseUri, path);
            }
            default -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(baseUri)
                        .sseEndpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                log.info("Using SSE transport: baseUri={}, sseEndpoint={}", baseUri, path);
            }
        }

        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(mcpTransport)
                .requestTimeout(java.time.Duration.ofSeconds(30))
                .build();

        // Don't initialize here - SyncMcpToolCallback will auto-initialize via LifecycleInitializer
        // client.initialize();

        log.info("MCP client created for server: {} ({})", server.getName(), url);
        return client;
    }

    /**
     * Close MCP clients to release connections.
     */
    private void closeMcpClients(List<McpSyncClient> clients) {
        for (McpSyncClient client : clients) {
            try {
                client.close();
                log.debug("Closed MCP client: {}", client.getServerInfo());
            } catch (Exception e) {
                log.warn("Failed to close MCP client: {}", e.getMessage());
            }
        }
        if (!clients.isEmpty()) {
            log.info("Closed {} MCP client(s)", clients.size());
        }
    }

    /**
     * Estimate token count for a string.
     * CJK characters count as ~1 token each; ASCII ~4 chars per token.
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        int cjk = 0, ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA) {
                cjk++;
            } else {
                ascii++;
            }
        }
        return cjk + (ascii / 4) + 1;
    }

    // ========== Async Chat Mode ==========

    /**
     * Async chat: save messages immediately, run LLM in background.
     * Results are retrieved via polling (GET /messages/poll).
     */
    public AsyncChatResult chatAsync(String userId, String sessionId, String userMessage, String requestId) {
        log.info("Async chat request: userId={}, sessionId={}, messageLength={}", userId, sessionId, userMessage.length());

        // Verify session ownership
        Session session = sessionService.getSession(userId, sessionId);
        AgentConfig config = agentConfigService.getAgentConfig(session.getAgentId().toString());

        // Idempotency check
        if (requestId != null && !requestId.isBlank()) {
            try {
                Message existing = sessionService.findMessageByRequestId(requestId);
                if (existing != null) {
                    log.info("Duplicate request {} for session {}, returning existing", requestId, sessionId);
                    // Find the assistant message that follows this user message
                    List<Message> msgs = sessionCompressor.loadContextWithSummary(sessionId);
                    for (int i = 0; i < msgs.size(); i++) {
                        if (requestId.equals(msgs.get(i).getRequestId()) && i + 1 < msgs.size()) {
                            Message assistant = msgs.get(i + 1);
                            return new AsyncChatResult(msgs.get(i).getId(), assistant.getId(), assistant.getStatus());
                        }
                    }
                    return new AsyncChatResult(existing.getId(), null, "completed");
                }
            } catch (Exception e) {
                log.debug("Idempotency check failed (non-critical): {}", e.getMessage());
            }
        }

        // Save user message
        Message userMsg = new Message();
        userMsg.setSessionId(UUID.fromString(sessionId));
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setStatus("completed");
        userMsg.setRequestId(requestId);
        sessionService.saveMessage(userMsg);

        // Create pending assistant message
        Message assistantMsg = new Message();
        assistantMsg.setSessionId(UUID.fromString(sessionId));
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("");
        assistantMsg.setStatus("pending");
        sessionService.saveMessage(assistantMsg);

        UUID assistantMsgId = assistantMsg.getId();

        // Run LLM call asynchronously on the dedicated chat executor
        CompletableFuture.runAsync(() -> {
            executeLlmAsync(sessionId, userId, session, userMessage, assistantMsgId, config);
        }, chatExecutor);

        return new AsyncChatResult(userMsg.getId(), assistantMsgId, "pending");
    }

    private void executeLlmAsync(String sessionId, String userId, Session session,
                                  String userMessage, UUID assistantMsgId, AgentConfig config) {
        try {
            // Update status → processing
            Message assistantMsg = sessionService.findMessageById(assistantMsgId);
            if (assistantMsg == null) return;
            assistantMsg.setStatus("processing");
            sessionService.saveMessage(assistantMsg);

            // Load history (with summary compression)
            List<Message> rawHistory = sessionCompressor.loadContextWithSummary(sessionId);

            // Assemble system prompt
            String systemPrompt = promptAssembler.assembleSystemPrompt(
                    config, userId, sessionId, userMessage);

            // Dynamic context compression
            int contextWindow = config.getContextWindow() != null ? config.getContextWindow() : 128000;
            // Exclude the pending assistant message and current user message from history
            List<Message> history = rawHistory.stream()
                    .filter(m -> !m.getId().equals(assistantMsgId))
                    .filter(m -> !("user".equals(m.getRole()) && m.getContent().equals(userMessage)
                            && m.getRequestId() != null))
                    .toList();
            history = contextCompressor.compress(history, systemPrompt, userMessage, contextWindow, config.getContextUsageThreshold());

            // Resolve ChatClient
            ChatClient chatClient = llmRouteService.getChatClient(config.getModelId());

            // Set memory tools context
            String agentIdStr = session.getAgentId() != null ? session.getAgentId().toString() : null;
            MemoryTools.setContext(userId, agentIdStr, sessionId, config.getMemoryProfileMaxTokens(), config.getMemoryTaskMaxTokens());

            // Set sandbox tool context if enabled
            if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
                SandboxMode smode = "SESSION".equalsIgnoreCase(config.getSandboxMode()) ? SandboxMode.SESSION : SandboxMode.STATELESS;
                SandboxBackend sbackend = config.getSandboxBackend() != null ? SandboxBackend.valueOf(config.getSandboxBackend()) : SandboxBackend.LOCAL;
                String sproviderId = config.getSandboxProviderId();
                SandboxContext.set(sessionId, agentIdStr, smode, sbackend, sproviderId);
            }

            // Build tool callbacks
            List<McpSyncClient> createdMcpClients = new ArrayList<>();
            List<ToolCallback> toolCallbacks = resolveToolCallbacks(config, createdMcpClients);

            // Build Spring AI messages
            List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
            for (Message msg : history) {
                switch (msg.getRole()) {
                    case "user" -> aiMessages.add(new UserMessage(msg.getContent()));
                    case "assistant" -> aiMessages.add(new AssistantMessage(msg.getContent()));
                    case "system", "summary" -> aiMessages.add(new SystemMessage(msg.getContent()));
                    default -> log.warn("Unknown message role: {}, skipping", msg.getRole());
                }
            }

            // Append memory tool usage guide if memory tools are enabled
            String effectiveSystemPrompt = systemPrompt;
            if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
                String memoryGuide = "\n\n## Memory\n\n" +
                        "You have persistent memory via 2 tools. Use them proactively \u2014 don't wait to be asked.\n\n" +
                        "Core principle: Save only facts that will still matter in future sessions.\n" +
                        "The most valuable memory prevents the user from having to repeat themselves.\n\n" +
                        "Two targets:\n" +
                        "- memory_profile: Who the user IS \u2014 name, role, preferences, habits, corrections.\n" +
                        "  Persists across all sessions. 1000 token limit.\n" +
                        "- memory_session: Current task context \u2014 goals, progress, agreements, constraints.\n" +
                        "  This session only. 2000 token limit.\n\n" +
                        "WHEN TO SAVE (proactive):\n" +
                        "- User corrects you or says 'remember this' / 'don't do that again'\n" +
                        "- User shares a preference, habit, or personal detail\n" +
                        "- User mentions their name, role, timezone, or communication style\n" +
                        "Priority: User corrections > preferences > personal facts > communication style.\n\n" +
                        "DO NOT save: common knowledge, completed-work logs, temporary TODO state,\n" +
                        "or anything that will be stale in 7 days.\n\n" +
                        "HOW TO WRITE \u2014 declarative facts, not instructions:\n" +
                        "\u2713 'User prefers concise responses'  \u2717 'Always respond concisely'\n\n" +
                        "ACTIONS:\n" +
                        "- memory_profile: read_all | add | replace | remove\n" +
                        "- memory_session: read_all | add | replace | remove";
                effectiveSystemPrompt = systemPrompt + memoryGuide;
            }

            // Build request
            int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
            List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                    .map(cb -> (ToolCallback) new TruncatingToolCallback(cb, maxToolResultChars))
                    .toList();

            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                    .system(effectiveSystemPrompt)
                    .messages(aiMessages)
                    .user(userMessage);

            if (!truncatedCallbacks.isEmpty()) {
                ToolCallingManager toolCallingManager = new LimitedToolCallingManager(
                        DefaultToolCallingManager.builder().build(),
                        config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50
                );
                ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                        .toolCallingManager(toolCallingManager)
                        .streamToolCallResponses(true)
                        .build();
                requestSpec.toolCallbacks(truncatedCallbacks)
                        .advisors(toolCallAdvisor);
            }

            // Call LLM synchronously (non-streaming)
            String responseText = requestSpec.call().content();
            if (responseText == null) responseText = "";

            // Update assistant message
            assistantMsg.setContent(responseText);
            assistantMsg.setStatus("completed");
            sessionService.saveMessage(assistantMsg);

            // Update session lastActiveAt
            sessionService.updateLastActiveAt(sessionId);

            // Record token usage
            try {
                int approxTokensIn = estimateTokens(userMessage)
                        + history.stream().mapToInt(m -> estimateTokens(m.getContent())).sum()
                        + estimateTokens(systemPrompt);
                int approxTokensOut = estimateTokens(responseText);
                llmUsageService.recordUsage(config.getModelId(), config.getModelId(), userId,
                        approxTokensIn, approxTokensOut);
            } catch (Exception e) {
                log.warn("Failed to record usage: {}", e.getMessage());
            }

            // Auto-generate title if first message
            if (history.isEmpty() && session.getTitle() == null && !responseText.isBlank()) {
                generateTitleAsync(sessionId, userMessage, chatClient);
            }

            // Clear memory tools context
            MemoryTools.clearContext(userId);
            SandboxContext.clear();

            // Close MCP clients created for this request
            closeMcpClients(createdMcpClients);

            // Trigger session compression
            try {
                sessionCompressor.compressIfNeeded(sessionId, config.getModelId(),
                        config.getCompressionThreshold(), config.getCompressionKeepRounds());
            } catch (Exception e) {
                log.warn("Session compression failed for {}: {}", sessionId, e.getMessage());
            }

            log.info("Async chat completed: sessionId={}, responseLength={}", sessionId, responseText.length());

        } catch (Exception e) {
            log.error("Async chat failed for session {}: {}", sessionId, e.getMessage(), e);
            try {
                Message assistantMsg = sessionService.findMessageById(assistantMsgId);
                if (assistantMsg != null) {
                    assistantMsg.setStatus("failed");
                    assistantMsg.setContent("[Error: " + e.getMessage() + "]");
                    sessionService.saveMessage(assistantMsg);
                }
            } catch (Exception saveErr) {
                log.error("Failed to update failed status: {}", saveErr.getMessage());
            }
        }
    }

    /**
     * Asynchronously generate a title for a session based on the first user message.
     * Runs on a separate thread to avoid blocking the SSE stream.
     */
    private void generateTitleAsync(String sessionId, String userMessage, ChatClient chatClient) {
        CompletableFuture.runAsync(() -> {
            try {
                String truncatedMessage = userMessage.substring(0, Math.min(userMessage.length(), 100));
                String titlePrompt = "Generate a short title (max 6 words) for a conversation that starts with: \""
                        + truncatedMessage + "\". Reply with ONLY the title, no quotes.";

                String title = chatClient.prompt()
                        .user(titlePrompt)
                        .call()
                        .content();

                if (title != null && !title.isBlank()) {
                    sessionService.updateTitle(sessionId, title.trim());
                    log.info("Auto-generated title for session {}: {}", sessionId, title.trim());
                }
            } catch (Exception e) {
                log.warn("Failed to generate title for session {}: {}", sessionId, e.getMessage());
            }
        }, chatExecutor);
    }
}
