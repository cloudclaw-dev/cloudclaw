package run.cloudclaw.mq.spi;

import run.cloudclaw.mq.model.CloudMessage;

import java.time.Duration;

/**
 * SPI interface for publishing messages to a topic.
 */
public interface MessagePublisher {

    /**
     * Publish a message to the specified topic.
     *
     * @param topic   the topic to publish to
     * @param message the message to publish
     */
    void publish(String topic, CloudMessage message);

    /**
     * Publish a message to the specified topic with a delay.
     *
     * @param topic   the topic to publish to
     * @param message the message to publish
     * @param delay   the delay before the message becomes available for consumption
     */
    void publishDelayed(String topic, CloudMessage message, Duration delay);
}
