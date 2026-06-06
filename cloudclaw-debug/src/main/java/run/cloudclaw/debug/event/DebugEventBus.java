package run.cloudclaw.debug.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple in-memory event bus for publishing real-time debug events
 * that are consumed by SSE streams in ChatEngine.
 *
 * <p>Events are published per session and consumed by the SSE sink
 * associated with that session's active chat stream.</p>
 */
@Slf4j
@Component
public class DebugEventBus {

    private final ConcurrentHashMap<String, List<Consumer<Map<String, Object>>>> subscribers = new ConcurrentHashMap<>();

    /**
     * Subscribe to debug events for a given session.
     */
    public void subscribe(String sessionId, Consumer<Map<String, Object>> consumer) {
        subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    /**
     * Unsubscribe all consumers for a given session.
     */
    public void unsubscribe(String sessionId) {
        subscribers.remove(sessionId);
    }

    /**
     * Publish a debug event to all subscribers for the given session.
     */
    public void publish(String sessionId, Map<String, Object> event) {
        List<Consumer<Map<String, Object>>> consumers = subscribers.get(sessionId);
        if (consumers != null) {
            for (Consumer<Map<String, Object>> consumer : consumers) {
                try {
                    consumer.accept(event);
                } catch (Exception e) {
                    log.debug("Failed to deliver debug event to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }
}
