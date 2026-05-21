package run.cloudclaw.mq.spi;

/**
 * SPI interface for subscribing to messages from a topic.
 */
public interface MessageSubscriber {

    /**
     * Subscribe to a topic within a consumer group.
     * Messages will be delivered to the provided handler.
     *
     * @param topic   the topic to subscribe to
     * @param group   the consumer group name
     * @param handler the handler to process received messages
     */
    void subscribe(String topic, String group, MessageHandler handler);

    /**
     * Unsubscribe from a topic within a consumer group.
     *
     * @param topic the topic to unsubscribe from
     * @param group the consumer group name
     */
    void unsubscribe(String topic, String group);
}
