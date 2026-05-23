package run.cloudclaw.standalone.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.session.cache.SessionCache;
import run.cloudclaw.session.cache.SessionCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Standalone session cache using Caffeine instead of Redis.
 */
@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneSessionCacheConfig {

    @Bean
    @ConditionalOnMissingBean
    public SessionCache sessionCache(SessionCacheProperties properties) {
        log.info("Creating Caffeine-backed SessionCache for standalone mode");
        return new StandaloneSessionCache(properties);
    }

    static class StandaloneSessionCache extends SessionCache {

        private static final String CONTEXT_KEY_PREFIX = "cloudclaw:session:";
        private static final String CONTEXT_KEY_SUFFIX = ":context";

        private final Cache<String, String> cache;
        private final SessionCacheProperties properties;
        private final ObjectMapper objectMapper;

        public StandaloneSessionCache(SessionCacheProperties properties) {
            super(null, properties);
            this.properties = properties;
            this.cache = Caffeine.newBuilder()
                    .maximumSize(10000)
                    .expireAfterWrite(properties.getSessionTtl())
                    .build();
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public void saveContext(String sessionId, List<Message> messages) {
            String key = buildContextKey(sessionId);
            try {
                String json = objectMapper.writeValueAsString(messages);
                cache.put(key, json);
                log.debug("Saved context for session {} with {} messages (Caffeine)", sessionId, messages.size());
            } catch (Exception e) {
                log.error("Failed to save context for session {}", sessionId, e);
            }
        }

        @Override
        public List<Message> loadContext(String sessionId) {
            String key = buildContextKey(sessionId);
            String json = cache.getIfPresent(key);
            if (json == null) return null;
            try {
                return objectMapper.readValue(json, new TypeReference<List<Message>>() {});
            } catch (Exception e) {
                log.error("Failed to load context for session {}", sessionId, e);
                return null;
            }
        }

        @Override
        public void evictContext(String sessionId) {
            cache.invalidate(buildContextKey(sessionId));
        }

        @Override
        public void saveSession(String sessionId, Session session) {
            String key = buildSessionKey(sessionId);
            try {
                cache.put(key, objectMapper.writeValueAsString(session));
            } catch (Exception e) {
                log.error("Failed to save session {}", sessionId, e);
            }
        }

        @Override
        public Session loadSession(String sessionId) {
            String json = cache.getIfPresent(buildSessionKey(sessionId));
            if (json == null) return null;
            try {
                return objectMapper.readValue(json, Session.class);
            } catch (Exception e) {
                log.error("Failed to load session {}", sessionId, e);
                return null;
            }
        }

        @Override
        public void evictSession(String sessionId) {
            cache.invalidate(buildSessionKey(sessionId));
        }

        private String buildContextKey(String sessionId) { return CONTEXT_KEY_PREFIX + sessionId + CONTEXT_KEY_SUFFIX; }
        private String buildSessionKey(String sessionId) { return CONTEXT_KEY_PREFIX + sessionId; }
    }
}
