package run.cloudclaw.mq.spi;

/**
 * SPI interface for message queue providers.
 * <p>
 * Implementations provide the underlying message queue infrastructure
 * (e.g., Redis Streams, RocketMQ, Kafka).
 * </p>
 */
public interface MessageQueueProvider extends AutoCloseable {

    /**
     * Create a new message publisher.
     *
     * @return a new MessagePublisher instance
     */
    MessagePublisher createPublisher();

    /**
     * Create a new message subscriber.
     *
     * @return a new MessageSubscriber instance
     */
    MessageSubscriber createSubscriber();

    /**
     * Check if the message queue provider is healthy and reachable.
     *
     * @return true if the provider is healthy, false otherwise
     */
    boolean isHealthy();
}
