package run.cloudclaw.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the CloudClaw message queue module.
 * <p>
 * Prefix: {@code cloudclaw.mq}
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "cloudclaw.mq")
public class MqProperties {

    /**
     * The message queue provider to use. Supported values: redis, rocketmq, kafka.
     * Defaults to "redis".
     */
    private String provider = "redis";

    /**
     * Redis-specific configuration.
     */
    private Redis redis = new Redis();

    @Data
    public static class Redis {

        /**
         * The consumer group name for Redis Stream consumers.
         */
        private String consumerGroup = "cloudclaw";

        /**
         * Number of messages to read in a single batch from the Redis Stream.
         */
        private int batchSize = 10;

        /**
         * Timeout in milliseconds for blocking stream reads.
         */
        private long blockTimeout = 5000;
    }
}
