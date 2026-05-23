package run.cloudclaw.mq.config;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.config.NoOpConfigChangeNotifier;
import run.cloudclaw.mq.redis.RedisStreamProvider;
import run.cloudclaw.mq.spi.MessageQueueProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Set;

/**
 * Auto-configuration for the CloudClaw message queue module.
 * <p>
 * Activates when {@code cloudclaw.mq.provider} is set to "redis" (default).
 * Creates a {@link RedisStreamProvider} as the {@link MessageQueueProvider} implementation.
 * </p>
 * <p>
 * 同时注册 {@link RedisConfigChangeNotifier} 用于集群模式下的配置热更新通知。
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "cloudclaw.mq.provider", havingValue = "redis", matchIfMissing = true)
@EnableConfigurationProperties(MqProperties.class)
public class MqAutoConfiguration {

    /** channel 前缀 */
    static final String CHANNEL_PREFIX = "cloudclaw:config:";

    /** 支持的实体类型 */
    static final Set<String> ENTITY_TYPES = Set.of("skill", "agent", "mcp", "llm");

    @Bean
    public MessageQueueProvider messageQueueProvider(
            RedisTemplate<String, Object> redisTemplate,
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            MqProperties properties) {
        log.info("Initializing Redis Streams message queue provider");
        return new RedisStreamProvider(redisTemplate, connectionFactory, objectMapper, properties);
    }

    /**
     * 集群模式：Redis 配置变更通知器 + 订阅容器。
     */
    @Bean
    @ConditionalOnProperty(name = "cloudclaw.mode", havingValue = "cluster")
    public RedisConfigChangeNotifier redisConfigChangeNotifier(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            CacheManager cacheManager,
            ApplicationEventPublisher eventPublisher) {
        log.info("初始化 Redis 配置变更通知器（集群模式）");
        RedisConfigChangeNotifier notifier = new RedisConfigChangeNotifier(redisTemplate, objectMapper, cacheManager);
        notifier.setEventPublisher(eventPublisher);
        return notifier;
    }

    @Bean
    @ConditionalOnProperty(name = "cloudclaw.mode", havingValue = "cluster")
    public RedisMessageListenerContainer configChangeSubscriber(
            RedisConnectionFactory connectionFactory,
            RedisConfigChangeNotifier notifier) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        for (String entityType : ENTITY_TYPES) {
            container.addMessageListener(notifier, new ChannelTopic(CHANNEL_PREFIX + entityType));
        }
        log.info("Redis 配置变更订阅已注册: {}", ENTITY_TYPES);
        return container;
    }

    /**
     * Standalone 模式：no-op 通知器。
     */
    @Bean
    @ConditionalOnMissingBean(ConfigChangeNotifier.class)
    public ConfigChangeNotifier noOpConfigChangeNotifier() {
        log.info("使用 NoOp 配置变更通知器（standalone 模式）");
        return new NoOpConfigChangeNotifier();
    }
}
