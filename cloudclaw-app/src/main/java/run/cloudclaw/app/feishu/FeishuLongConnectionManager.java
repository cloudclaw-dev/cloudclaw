package run.cloudclaw.app.feishu;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.auth.service.ChannelLifecycleManager;
import run.cloudclaw.common.model.ChannelConfig;

import jakarta.annotation.PreDestroy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Feishu WebSocket long-connection clients.
 *
 * <p>On application startup, reads all enabled Feishu channel configs and creates
 * a {@link Client} for each. The SDK handles heartbeat, reconnection, and event
 * dispatching internally.</p>
 *
 * <p>Implements {@link ChannelLifecycleManager} so that {@link ChannelConfigService}
 * can dynamically start/stop/restart individual clients at runtime.</p>
 *
 * <p>Active only when {@code cloudclaw.feishu.event-mode=long-connection} (default).
 * When set to {@code callback}, this bean is not created and HTTP callback mode is used.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cloudclaw.feishu.event-mode", havingValue = "long-connection", matchIfMissing = true)
public class FeishuLongConnectionManager implements ChannelLifecycleManager {

    private static final String CHANNEL_TYPE = "feishu";

    private final ChannelConfigService channelConfigService;
    private final FeishuMessageHandler messageHandler;

    /** configId -> WS Client mapping for lifecycle management */
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public FeishuLongConnectionManager(ChannelConfigService channelConfigService,
                                        FeishuMessageHandler messageHandler) {
        this.channelConfigService = channelConfigService;
        this.messageHandler = messageHandler;
    }

    /**
     * Start WS clients for all enabled Feishu channel configs on application ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        // Only start WS for configs with purpose=bot or purpose=both
        List<ChannelConfig> feishuConfigs = channelConfigService
                .findByChannelTypeEnabledAndPurpose(CHANNEL_TYPE, java.util.List.of("bot", "both"));

        if (feishuConfigs.isEmpty()) {
            log.info("No enabled Feishu channel configs found, skipping long-connection startup");
            return;
        }

        log.info("Starting Feishu long-connection for {} config(s)", feishuConfigs.size());
        for (ChannelConfig config : feishuConfigs) {
            try {
                startClient(config);
            } catch (Exception e) {
                log.error("Failed to start Feishu WS client for configId={}: {}", config.getId(), e.getMessage(), e);
                channelConfigService.updateConnectionStatus(config.getId(), "error");
            }
        }
    }

    // ================================================================
    // ChannelLifecycleManager implementation
    // ================================================================

    @Override
    public void startClient(ChannelConfig config) {
        if (clients.containsKey(config.getId())) {
            log.warn("Client already running for configId={}, skipping", config.getId());
            return;
        }

        try {
            String appSecret = channelConfigService.getDecryptedSecret(config);

            EventDispatcher eventHandler = EventDispatcher.newBuilder("", "")
                    .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                        @Override
                        public void handle(P2MessageReceiveV1 event) throws Exception {
                            handleP2MessageReceiveV1(config, event);
                        }
                    })
                    .build();

            Client client = new Client.Builder(config.getAppId(), appSecret)
                    .eventHandler(eventHandler)
                    .build();

            log.info("Starting Feishu WS client for configId={}, appId={}", config.getId(), config.getAppId());
            client.start();

            clients.put(config.getId(), client);
            channelConfigService.updateConnectionStatus(config.getId(), "connected");
            log.info("Feishu WS client registered for configId={}", config.getId());
        } catch (Exception e) {
            log.error("Failed to start Feishu WS client for configId={}: {}", config.getId(), e.getMessage(), e);
            channelConfigService.updateConnectionStatus(config.getId(), "error");
            throw new RuntimeException("Failed to start Feishu WS client: " + e.getMessage(), e);
        }
    }

    @Override
    public void stopClient(String configId) {
        Client client = clients.remove(configId);
        if (client != null) {
            disconnectClient(client);
            log.info("Feishu WS client stopped for configId={}", configId);
        } else {
            log.debug("No active Feishu WS client for configId={}, nothing to stop", configId);
        }
        channelConfigService.updateConnectionStatus(configId, "disconnected");
    }

    @Override
    public void restartClient(String configId) {
        stopClient(configId);
        ChannelConfig config = channelConfigService.findById(configId);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            startClient(config);
        } else {
            log.info("Config {} not found or disabled, not restarting client", configId);
        }
    }

    @Override
    public boolean isHandledByWs(String configId) {
        return clients.containsKey(configId);
    }

    // ================================================================
    // Status query
    // ================================================================

    /**
     * Get the connection status of all active clients.
     *
     * @return map of configId to status string
     */
    public Map<String, String> getStatus() {
        Map<String, String> statusMap = new HashMap<>();
        for (String configId : clients.keySet()) {
            statusMap.put(configId, "connected");
        }
        return statusMap;
    }

    /**
     * Get the connection status for a specific config.
     */
    public String getStatus(String configId) {
        return clients.containsKey(configId) ? "connected" : "disconnected";
    }

    // ================================================================
    // Event handling
    // ================================================================

    /**
     * Handle a P2MessageReceiveV1 event from the Feishu SDK.
     * Must return quickly (within 3s) to avoid event re-push.
     */
    private void handleP2MessageReceiveV1(ChannelConfig config, P2MessageReceiveV1 event) {
        try {
            var data = event.getEvent();
            if (data == null || data.getMessage() == null) {
                log.warn("Received empty Feishu message event");
                return;
            }

            String openId = "";
            if (data.getSender() != null && data.getSender().getSenderId() != null) {
                openId = data.getSender().getSenderId().getOpenId();
            }

            String chatId = data.getMessage().getChatId();
            String chatType = data.getMessage().getChatType() != null ? data.getMessage().getChatType() : "p2p";
            String msgId = data.getMessage().getMessageId();
            String msgType = data.getMessage().getMessageType() != null ? data.getMessage().getMessageType() : "text";
            String content = data.getMessage().getContent() != null ? data.getMessage().getContent() : "";

            String userMessage = messageHandler.extractText(content, msgType);
            if (userMessage == null || userMessage.isBlank()) {
                log.info("Empty or unsupported message type ({}) from Feishu, ignoring", msgType);
                return;
            }

            log.info("Feishu WS message: openId={}, chatId={}, type={}, text={}",
                    openId, chatId, chatType,
                    userMessage.substring(0, Math.min(50, userMessage.length())));

            // Delegate to handler (dedup + thread pool + callback)
            messageHandler.handleMessageFromWs(config, openId, chatId, chatType, msgId, userMessage);
        } catch (Exception e) {
            log.error("Error handling Feishu WS event for configId={}", config.getId(), e);
        }
    }

    // ================================================================
    // Internal: client disconnection via reflection
    // ================================================================

    /**
     * Disconnect a Feishu SDK {@link Client} by calling its protected {@code disconnect()} method.
     * The SDK doesn't expose a public close/stop method, so we use reflection.
     */
    private void disconnectClient(Client client) {
        try {
            Method disconnect = Client.class.getDeclaredMethod("disconnect");
            disconnect.setAccessible(true);
            disconnect.invoke(client);
        } catch (Exception e) {
            log.warn("Failed to disconnect Feishu WS client via reflection: {}", e.getMessage());
        }
    }

    // ================================================================
    // Cleanup
    // ================================================================

    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all Feishu WS clients ({})", clients.size());
        for (Map.Entry<String, Client> entry : clients.entrySet()) {
            try {
                disconnectClient(entry.getValue());
                log.info("Closed WS client for configId={}", entry.getKey());
            } catch (Exception e) {
                log.error("Error closing WS client for configId={}", entry.getKey(), e);
            }
        }
        clients.clear();
    }
}
