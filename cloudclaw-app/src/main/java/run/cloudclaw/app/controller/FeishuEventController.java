package run.cloudclaw.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import run.cloudclaw.app.feishu.FeishuMessageHandler;
import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.common.model.ChannelConfig;

/**
 * Feishu bot HTTP event callback controller.
 *
 * <p>Handles HTTP events from Feishu open platform:</p>
 * <ul>
 *   <li>url_verification - Feishu URL validation (returns challenge)</li>
 *   <li>im.message.receive_v1 - incoming message from Feishu user</li>
 * </ul>
 *
 * <p>Message processing is delegated to {@link FeishuMessageHandler} which uses
 * the callback mechanism (no DB polling). This controller is active only when
 * {@code cloudclaw.feishu.event-mode=callback}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/channel/feishu/event")
@ConditionalOnProperty(name = "cloudclaw.feishu.event-mode", havingValue = "callback")
public class FeishuEventController {

    private final ChannelConfigService channelConfigService;
    private final FeishuMessageHandler messageHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeishuEventController(ChannelConfigService channelConfigService,
                                  FeishuMessageHandler messageHandler) {
        this.channelConfigService = channelConfigService;
        this.messageHandler = messageHandler;
    }

    /**
     * Feishu event callback endpoint.
     * URL format: POST /api/v1/channel/feishu/event/{configId}
     */
    @PostMapping(value = "/{configId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleEvent(
            @PathVariable String configId,
            @RequestBody String body) {

        log.info("Feishu event received for configId={}", configId);

        try {
            JsonNode event = objectMapper.readTree(body);

            // 1. URL verification challenge
            if (event.has("challenge")) {
                String challenge = event.path("challenge").asText();
                String token = event.path("token").asText();
                log.info("Feishu URL verification for configId={}", configId);

                ChannelConfig config = channelConfigService.findById(configId);
                if (config != null && token.equals(config.getVerificationToken())) {
                    return ResponseEntity.ok("{\"challenge\":\"" + challenge + "\"}");
                }
                log.warn("URL verification token mismatch for configId={}", configId);
                return ResponseEntity.badRequest().body("{\"error\":\"token mismatch\"}");
            }

            // 2. Message event
            JsonNode header = event.path("header");
            String eventType = header.path("event_type").asText();
            String appId = header.path("app_id").asText();

            if (!"im.message.receive_v1".equals(eventType)) {
                log.info("Ignoring event type: {}", eventType);
                return ResponseEntity.ok("{}");
            }

            ChannelConfig config = channelConfigService.findById(configId);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                log.warn("Channel config not found or disabled: configId={}", configId);
                return ResponseEntity.ok("{}");
            }

            if (config.getAppId() != null && !config.getAppId().equals(appId)) {
                log.warn("App ID mismatch: expected={}, got={}", config.getAppId(), appId);
                return ResponseEntity.ok("{}");
            }

            // Extract message details
            JsonNode eventBody = event.path("event");
            JsonNode sender = eventBody.path("sender");
            JsonNode senderId = sender.path("sender_id");
            String openId = senderId.path("open_id").asText();

            JsonNode message = eventBody.path("message");
            String chatId = message.path("chat_id").asText();
            String chatType = message.path("chat_type").asText("p2p");
            String msgType = message.path("message_type").asText("text");
            String msgId = message.path("message_id").asText();
            String content = message.path("content").asText();

            String userMessage = messageHandler.extractText(content, msgType);
            if (userMessage == null || userMessage.isBlank()) {
                log.info("Empty message, ignoring");
                return ResponseEntity.ok("{}");
            }

            log.info("Feishu message: openId={}, chatId={}, type={}, text={}",
                    openId, chatId, chatType,
                    userMessage.substring(0, Math.min(50, userMessage.length())));

            // Delegate to shared handler (dedup + thread pool + callback reply)
            messageHandler.handleMessageFromCallback(config, openId, chatId, chatType, msgId, userMessage);

            return ResponseEntity.ok("{}");

        } catch (Exception e) {
            log.error("Error handling Feishu event for configId={}", configId, e);
            return ResponseEntity.ok("{}");
        }
    }
}
