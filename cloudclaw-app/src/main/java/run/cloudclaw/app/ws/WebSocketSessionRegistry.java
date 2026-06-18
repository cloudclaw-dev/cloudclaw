package run.cloudclaw.app.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping userId → active WebSocket sessions.
 *
 * <p>A single user may have multiple WS connections (e.g. multiple browser tabs).
 * All lookups are thread-safe via {@link ConcurrentHashMap}.</p>
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /**
     * Register a session for a user.
     */
    public void register(String userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("Registered WS session {} for user {}", session.getId(), userId);
    }

    /**
     * Unregister a session for a user.
     */
    public void unregister(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId, sessions);
            }
        }
        log.debug("Unregistered WS session {} for user {}", session.getId(), userId);
    }

    /**
     * Get all sessions for a user.
     */
    public Set<WebSocketSession> getSessions(String userId) {
        return userSessions.getOrDefault(userId, Set.of());
    }

    /**
     * Get all active sessions across all users (for dead-connection eviction).
     */
    public Collection<WebSocketSession> getAllSessions() {
        return userSessions.values().stream()
                .flatMap(Set::stream)
                .toList();
    }
}
