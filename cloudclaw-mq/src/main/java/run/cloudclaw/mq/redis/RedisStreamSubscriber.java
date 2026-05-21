package run.cloudclaw.mq.redis;

import run.cloudclaw.mq.model.CloudMessage;
import run.cloudclaw.mq.spi.MessageHandler;
import run.cloudclaw.mq.spi.MessageSubscriber;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Redis Streams-based implementation of {@link MessageSubscriber}.
 * <p>
 * Uses consumer groups to read from Redis Streams in a blocking manner.
 * Each subscription runs on a dedicated thread to perform blocking reads.
 * </p>
 */
@Slf4j
public class RedisStreamSubscriber implements MessageSubscriber {

    private static final String KEY_PREFIX = "cloudclaw:mq:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration blockTimeout;
    private final int batchSize;

    private final ConcurrentHashMap<String, Future<?>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "cloudclaw-mq-subscriber");
        t.setDaemon(true);
        return t;
    });

    public RedisStreamSubscriber(RedisTemplate<String, Object> redisTemplate,
                                 ObjectMapper objectMapper,
                                 Duration blockTimeout,
                                 int batchSize) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.blockTimeout = blockTimeout;
        this.batchSize = batchSize;
    }

    @Override
    public void subscribe(String topic, String group, MessageHandler handler) {
        String subscriptionKey = topic + ":" + group;
        if (subscriptions.containsKey(subscriptionKey)) {
            log.warn("Already subscribed to topic [{}] with group [{}], ignoring duplicate", topic, group);
            return;
        }

        String streamKey = KEY_PREFIX + topic;
        log.info("Subscribing to stream [{}] with consumer group [{}]", streamKey, group);

        Future<?> future = executor.submit(() -> {
            StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
            String consumerName = "consumer-" + Thread.currentThread().getId();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<MapRecord<String, Object, Object>> records =
                            (List<MapRecord<String, Object, Object>>) streamOps.read(
                                    Consumer.from(group, consumerName),
                                    batchReadOptions(),
                                    StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                            );

                    if (records == null || records.isEmpty()) {
                        continue;
                    }

                    for (MapRecord<String, Object, Object> record : records) {
                        try {
                            CloudMessage message = deserializeMessage(record, topic);
                            log.debug("Received message [{}] from stream [{}]", message.getId(), streamKey);
                            handler.handle(message);
                            // Acknowledge the message
                            streamOps.acknowledge(streamKey, group, record.getId());
                        } catch (Exception e) {
                            log.error("Failed to process message from stream [{}], record [{}]: {}",
                                    streamKey, record.getId(), e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error reading from stream [{}]: {}, retrying in 1s...", streamKey, e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Subscriber for topic [{}] with group [{}] stopped", topic, group);
        });

        subscriptions.put(subscriptionKey, future);
    }

    @Override
    public void unsubscribe(String topic, String group) {
        String subscriptionKey = topic + ":" + group;
        Future<?> future = subscriptions.remove(subscriptionKey);
        if (future != null) {
            future.cancel(true);
            log.info("Unsubscribed from topic [{}] with group [{}]", topic, group);
        }
    }

    /**
     * Build the batch read options for Redis Stream reads.
     */
    private org.springframework.data.redis.connection.stream.StreamReadOptions batchReadOptions() {
        return org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                .count(batchSize)
                .block(blockTimeout);
    }

    /**
     * Deserialize a Redis Stream record into a CloudMessage.
     */
    @SuppressWarnings("unchecked")
    private CloudMessage deserializeMessage(MapRecord<String, Object, Object> record, String topic)
            throws JsonProcessingException {
        Map<Object, Object> raw = record.getValue();

        Map<String, String> headers = new HashMap<>();
        if (raw.containsKey("headers")) {
            headers = objectMapper.readValue((String) raw.get("headers"),
                    new TypeReference<Map<String, String>>() {});
        }

        Object payload = null;
        if (raw.containsKey("payload")) {
            String payloadJson = (String) raw.get("payload");
            payload = objectMapper.readValue(payloadJson, Object.class);
        }

        return CloudMessage.builder()
                .id((String) raw.get("id"))
                .topic(topic)
                .headers(headers)
                .payload(payload)
                .timestamp(Long.parseLong((String) raw.get("timestamp")))
                .build();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RedisStreamSubscriber, cancelling {} subscriptions", subscriptions.size());
        subscriptions.forEach((key, future) -> future.cancel(true));
        subscriptions.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
