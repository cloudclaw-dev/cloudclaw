package run.cloudclaw.mq.spi;

import run.cloudclaw.mq.model.CloudMessage;

/**
 * Functional interface for handling received messages.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Handle a received message.
     *
     * @param message the message to handle
     */
    void handle(CloudMessage message);
}
