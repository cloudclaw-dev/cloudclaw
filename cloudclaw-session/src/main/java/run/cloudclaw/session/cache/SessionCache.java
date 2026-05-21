package run.cloudclaw.session.cache;

import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based cache for session data and message context.
 *
 * <p>Provides caching for:
 * <ul>
 *     <li>Session context (message history) with key pattern {@code cloudclaw:session:{sessionId}:context}</li>
 *     <li>Session objects with key pattern {@code cloudclaw:session:{sessionId}</li>
 * </ul>
 */
@Slf4j
public class SessionCache {

    private static final String CONTEXT_KEY_PREFIX = "cloudclaw:session:";
    private static final String CONTEXT_KEY_SUFFIX = ":context";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionCacheProperties properties;
    private final ObjectMapper objectMapper;

    public SessionCache(RedisTemplate<String, Object> redisTemplate,
                        SessionCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== Context (Message History) Operations ====================

    /**
     * Save message context to Redis cache.
     *
     * @param sessionId the session ID
     * @param messages  the list of messages to cache
     */
    public void saveContext(String sessionId, List<Message> messages) {
        String key = buildContextKey(sessionId);
        try {
            String json = objectMapper.writeValueAsString(messages);
            long ttlMinutes = properties.getSessionTtl().toMinutes();
            redisTemplate.opsForValue().set(key, json, ttlMinutes, TimeUnit.MINUTES);
            log.debug("Saved context for session {} with {} messages, TTL={}min", sessionId, messages.size(), ttlMinutes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize context for session {}", sessionId, e);
        }
    }

    /**
     * Load message context from Redis cache.
     *
     * @param sessionId the session ID
     * @return the list of cached messages, or {@code null} on cache miss
     */
    public List<Message> loadContext(String sessionId) {
        String key = buildContextKey(sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.debug("Cache miss for session context: {}", sessionId);
            return null;
        }
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            List<Message> messages = objectMapper.readValue(json, new TypeReference<List<Message>>() {});
            log.debug("Cache hit for session context: {}, {} messages", sessionId, messages.size());
            return messages;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize context for session {}", sessionId, e);
            return null;
        }
    }

    /**
     * Remove message context from Redis cache.
     *
     * @param sessionId the session ID
     */
    public void evictContext(String sessionId) {
        String key = buildContextKey(sessionId);
        redisTemplate.delete(key);
        log.debug("Evicted context for session {}", sessionId);
    }

    // ==================== Session Object Operations ====================

    /**
     * Save a session object to Redis cache.
     *
     * @param sessionId the session ID (used as part of the cache key)
     * @param session   the session object to cache
     */
    public void saveSession(String sessionId, Session session) {
        String key = buildSessionKey(sessionId);
        try {
            String json = objectMapper.writeValueAsString(session);
            long ttlMinutes = properties.getSessionTtl().toMinutes();
            redisTemplate.opsForValue().set(key, json, ttlMinutes, TimeUnit.MINUTES);
            log.debug("Saved session {} to cache, TTL={}min", sessionId, ttlMinutes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session {}", sessionId, e);
        }
    }

    /**
     * Load a session object from Redis cache.
     *
     * @param sessionId the session ID
     * @return the cached session, or {@code null} on cache miss
     */
    public Session loadSession(String sessionId) {
        String key = buildSessionKey(sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.debug("Cache miss for session: {}", sessionId);
            return null;
        }
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            Session session = objectMapper.readValue(json, Session.class);
            log.debug("Cache hit for session: {}", sessionId);
            return session;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize session {}", sessionId, e);
            return null;
        }
    }

    /**
     * Remove a session object from Redis cache.
     *
     * @param sessionId the session ID
     */
    public void evictSession(String sessionId) {
        String key = buildSessionKey(sessionId);
        redisTemplate.delete(key);
        log.debug("Evicted session object for session {}", sessionId);
    }

    // ==================== Key Builders ====================

    private String buildContextKey(String sessionId) {
        return CONTEXT_KEY_PREFIX + sessionId + CONTEXT_KEY_SUFFIX;
    }

    private String buildSessionKey(String sessionId) {
        return CONTEXT_KEY_PREFIX + sessionId;
    }
}
