package run.cloudclaw.app.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import run.cloudclaw.agent.engine.ChatEngine;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.session.service.SessionService;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Core WebSocket handler for real-time chat.
 *
 * <p>Manages the full lifecycle of a WebSocket chat connection:</p>
 * <ul>
 *   <li>Connection establishment — sends a {@code connected} confirmation frame</li>
 *   <li>Chat requests — subscribes to {@link ChatEngine#chat} Flux and streams {@link ChatChunk}s</li>
 *   <li>Cancel — disposes the active chat subscription for a session</li>
 *   <li>Heartbeat — responds to {@code ping} with {@code pong}; updates last-ping timestamp</li>
 *   <li>Dead-connection eviction — scheduled scan closes sessions with no ping in 90s</li>
 * </ul>
 *
 * <p>Concurrency: each session can have only one active chat at a time.
 * Starting a new chat automatically cancels the previous one.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatEngine chatEngine;
    private final SessionService sessionService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Active chat subscriptions keyed by session ID (WebSocket session, not chat session). */
    private final ConcurrentMap<String, ConcurrentMap<String, reactor.core.Disposable>> activeChats = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        sessionRegistry.register(userId, session);
        activeChats.put(session.getId(), new ConcurrentHashMap<>());

        // Send connected confirmation frame
        sendFrame(session, Map.of("type", "connected", "userId", userId, "username", username != null ? username : ""));
        log.info("WebSocket connected: sessionId={}, userId={}", session.getId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            WsMessage wsMsg = objectMapper.readValue(message.getPayload(), WsMessage.class);
            String action = wsMsg.getAction();

            if (action == null) {
                sendError(session, ErrorCode.BAD_REQUEST, "Missing action field");
                return;
            }

            switch (action) {
                case "chat" -> handleChat(session, wsMsg);
                case "cancel" -> handleCancel(session, wsMsg);
                case "ping" -> handlePing(session);
                default -> sendError(session, ErrorCode.BAD_REQUEST, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error processing WS message from session {}: {}", session.getId(), e.getMessage(), e);
            sendError(session, ErrorCode.LLM_CALL_FAILED, "Failed to process message");
        }
    }

    /**
     * Handle a chat request: subscribe to ChatEngine.chat() Flux and stream chunks.
     */
    private void handleChat(WebSocketSession session, WsMessage wsMsg) {
        String userId = (String) session.getAttributes().get("userId");
        String chatSessionId = wsMsg.getSessionId();

        if (chatSessionId == null || chatSessionId.isBlank()) {
            sendError(session, ErrorCode.BAD_REQUEST, "Missing sessionId");
            return;
        }
        if (wsMsg.getMessage() == null || wsMsg.getMessage().isBlank()) {
            sendError(session, ErrorCode.BAD_REQUEST, "Missing message");
            return;
        }

        // Verify session ownership
        try {
            sessionService.getSession(userId, chatSessionId);
        } catch (Exception e) {
            sendError(session, ErrorCode.NOT_FOUND, "Session not found or not owned by user");
            return;
        }

        // Cancel any previous active chat for this chat session
        cancelActiveChat(session.getId(), chatSessionId);

        log.info("WS chat start: wsSession={}, chatSession={}, userId={}", session.getId(), chatSessionId, userId);

        reactor.core.Disposable subscription = chatEngine.chat(userId, chatSessionId, wsMsg.getMessage())
                .subscribe(
                        chunk -> sendChunk(session, chunk),
                        error -> {
                            log.error("WS chat error for chatSession {}: {}", chatSessionId, error.getMessage());
                            sendError(session, ErrorCode.LLM_CALL_FAILED, error.getMessage());
                            removeActiveChat(session.getId(), chatSessionId);
                        },
                        () -> {
                            // ChatChunk.done() is emitted by ChatEngine, so just clean up here
                            removeActiveChat(session.getId(), chatSessionId);
                        }
                );

        activeChats.get(session.getId()).put(chatSessionId, subscription);
    }

    /**
     * Handle a cancel request: dispose the active chat subscription.
     */
    private void handleCancel(WebSocketSession session, WsMessage wsMsg) {
        String chatSessionId = wsMsg.getSessionId();
        if (chatSessionId == null) {
            sendError(session, ErrorCode.BAD_REQUEST, "Missing sessionId for cancel");
            return;
        }
        cancelActiveChat(session.getId(), chatSessionId);
        sendChunk(session, ChatChunk.done());
    }

    /**
     * Handle a ping: respond with pong and update last-ping timestamp.
     */
    private void handlePing(WebSocketSession session) {
        session.getAttributes().put("lastPing", System.currentTimeMillis());
        sendFrame(session, Map.of("type", "pong"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        sessionRegistry.unregister(userId, session);

        // Dispose all active chats for this WS connection
        ConcurrentMap<String, reactor.core.Disposable> chats = activeChats.remove(session.getId());
        if (chats != null) {
            chats.values().forEach(sub -> {
                if (!sub.isDisposed()) {
                    sub.dispose();
                }
            });
        }
        log.info("WebSocket closed: sessionId={}, userId={}, status={}", session.getId(), userId, status);
    }

    /**
     * Scheduled task: evict dead connections with no ping in the last 90 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void evictDeadConnections() {
        long threshold = System.currentTimeMillis() - 90_000;
        for (WebSocketSession session : sessionRegistry.getAllSessions()) {
            Long lastPing = (Long) session.getAttributes().get("lastPing");
            if (lastPing != null && lastPing < threshold) {
                log.info("Evicting dead WebSocket session {} (last ping {}ms ago)",
                        session.getId(), System.currentTimeMillis() - lastPing);
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException e) {
                    log.warn("Failed to close dead WS session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    // ===== Helper methods =====

    private void sendChunk(WebSocketSession session, ChatChunk chunk) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chunk)));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS chunk to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, ErrorCode errorCode, String detail) {
        sendFrame(session, Map.of(
                "type", "error",
                "errorCode", errorCode.getCode(),
                "errorI18nKey", errorCode.getI18nKey(),
                "errorDetail", detail != null ? detail : "",
                "done", true
        ));
    }

    private void sendFrame(WebSocketSession session, Map<String, Object> frame) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS frame to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void cancelActiveChat(String wsSessionId, String chatSessionId) {
        ConcurrentMap<String, reactor.core.Disposable> chats = activeChats.get(wsSessionId);
        if (chats != null) {
            reactor.core.Disposable sub = chats.remove(chatSessionId);
            if (sub != null && !sub.isDisposed()) {
                sub.dispose();
            }
        }
    }

    private void removeActiveChat(String wsSessionId, String chatSessionId) {
        ConcurrentMap<String, reactor.core.Disposable> chats = activeChats.get(wsSessionId);
        if (chats != null) {
            chats.remove(chatSessionId);
        }
    }
}
