package run.cloudclaw.mq.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a message in the CloudClaw message queue system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudMessage {

    /**
     * Unique identifier for this message.
     */
    private String id;

    /**
     * The topic this message belongs to.
     */
    private String topic;

    /**
     * Optional headers for message metadata.
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * The message payload. Will be serialized to JSON for transport.
     */
    private Object payload;

    /**
     * Timestamp of message creation in epoch milliseconds.
     */
    private long timestamp;

    /**
     * Static factory method to create a new CloudMessage.
     *
     * @param topic   the topic for the message
     * @param payload the message payload
     * @return a new CloudMessage with generated id and current timestamp
     */
    public static CloudMessage create(String topic, Object payload) {
        return CloudMessage.builder()
                .id(UUID.randomUUID().toString())
                .topic(topic)
                .payload(payload)
                .timestamp(Instant.now().toEpochMilli())
                .headers(new HashMap<>())
                .build();
    }
}
