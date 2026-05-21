package run.cloudclaw.mq.redis;

import run.cloudclaw.mq.model.CloudMessage;
import run.cloudclaw.mq.spi.MessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis Streams-based implementation of {@link MessagePublisher}.
 * <p>
 * Messages are published to Redis Streams with keys in the format
 * {@code cloudclaw:mq:{topic}}. The payload is serialized to JSON using Jackson.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStreamPublisher implements MessagePublisher {

    private static final String KEY_PREFIX = "cloudclaw:mq:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService delayScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cloudclaw-mq-delay-publisher");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void publish(String topic, CloudMessage message) {
        String streamKey = KEY_PREFIX + topic;
        try {
            Map<String, String> messageMap = serializeMessage(message);
            ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                    .ofObject(messageMap)
                    .withStreamKey(streamKey);

            redisTemplate.opsForStream().add(record);
            log.debug("Published message [{}] to stream [{}]", message.getId(), streamKey);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message payload for topic [{}]: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to serialize message payload", e);
        } catch (Exception e) {
            log.error("Failed to publish message [{}] to topic [{}]: {}", message.getId(), topic, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    @Override
    public void publishDelayed(String topic, CloudMessage message, Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            publish(topic, message);
            return;
        }
        log.debug("Scheduling delayed message [{}] to topic [{}] with delay [{}]",
                message.getId(), topic, delay);
        delayScheduler.schedule(() -> {
            try {
                publish(topic, message);
            } catch (Exception e) {
                log.error("Failed to publish delayed message [{}] to topic [{}]: {}",
                        message.getId(), topic, e.getMessage(), e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Serialize a CloudMessage into a flat Map suitable for Redis Stream storage.
     */
    private Map<String, String> serializeMessage(CloudMessage message) throws JsonProcessingException {
        Map<String, String> map = new HashMap<>();
        map.put("id", message.getId());
        map.put("topic", message.getTopic());
        map.put("timestamp", String.valueOf(message.getTimestamp()));
        map.put("payload", objectMapper.writeValueAsString(message.getPayload()));

        if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
            map.put("headers", objectMapper.writeValueAsString(message.getHeaders()));
        }

        return map;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down delayed message scheduler");
        delayScheduler.shutdown();
        try {
            if (!delayScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                delayScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            delayScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
