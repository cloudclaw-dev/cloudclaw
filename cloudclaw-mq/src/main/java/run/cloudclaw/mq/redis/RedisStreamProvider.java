package run.cloudclaw.mq.redis;

import run.cloudclaw.mq.config.MqProperties;
import run.cloudclaw.mq.spi.MessagePublisher;
import run.cloudclaw.mq.spi.MessageQueueProvider;
import run.cloudclaw.mq.spi.MessageSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Redis Streams-based implementation of {@link MessageQueueProvider}.
 * <p>
 * Uses Redis Streams as the underlying message queue. Consumer groups are
 * automatically created on startup for known topics.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStreamProvider implements MessageQueueProvider {

    private static final String KEY_PREFIX = "cloudclaw:mq:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final MqProperties properties;

    private static final List<String> KNOWN_TOPICS = List.of(
            "memory.extract",
            "memory.embedding"
    );

    @Override
    public MessagePublisher createPublisher() {
        return new RedisStreamPublisher(redisTemplate, objectMapper);
    }

    @Override
    public MessageSubscriber createSubscriber() {
        return new RedisStreamSubscriber(
                redisTemplate,
                objectMapper,
                Duration.ofMillis(properties.getRedis().getBlockTimeout()),
                properties.getRedis().getBatchSize()
        );
    }

    @Override
    public boolean isHealthy() {
        try {
            String pong = connectionFactory.getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.error("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create consumer groups for known topics on startup.
     * If the stream does not exist yet, it will be created with the consumer group.
     */
    @PostConstruct
    public void initConsumerGroups() {
        String group = properties.getRedis().getConsumerGroup();
        log.info("Initializing consumer groups for known topics, group=[{}]", group);

        for (String topic : KNOWN_TOPICS) {
            String streamKey = KEY_PREFIX + topic;
            try {
                // Try to create the consumer group. If the stream doesn't exist, create it from the beginning.
                redisTemplate.opsForStream().createGroup(streamKey, group);
                log.info("Created consumer group [{}] for stream [{}]", group, streamKey);
            } catch (Exception e) {
                // Group already exists is fine
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.debug("Consumer group [{}] already exists for stream [{}]", group, streamKey);
                } else {
                    log.warn("Failed to create consumer group [{}] for stream [{}]: {}",
                            group, streamKey, e.getMessage());
                }
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        log.info("Closing RedisStreamProvider");
    }
}
