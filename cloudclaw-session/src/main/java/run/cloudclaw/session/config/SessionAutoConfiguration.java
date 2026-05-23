package run.cloudclaw.session.config;

import run.cloudclaw.session.cache.SessionCache;
import run.cloudclaw.session.cache.SessionCacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Auto-configuration for the CloudClaw session module.
 *
 * <p>Registers the session cache bean and binds configuration properties.
 * This class is listed in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * for automatic discovery by Spring Boot.
 */
@AutoConfiguration
@EnableConfigurationProperties(SessionCacheProperties.class)
public class SessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionCache sessionCache(RedisTemplate<String, Object> redisTemplate,
                                     SessionCacheProperties properties) {
        return new SessionCache(redisTemplate, properties);
    }
}
