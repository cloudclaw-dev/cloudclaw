package run.cloudclaw.standalone.mq;

import run.cloudclaw.mq.model.CloudMessage;
import run.cloudclaw.mq.spi.MessageHandler;
import run.cloudclaw.mq.spi.MessagePublisher;
import run.cloudclaw.mq.spi.MessageQueueProvider;
import run.cloudclaw.mq.spi.MessageSubscriber;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory message queue provider for standalone mode.
 * <p>
 * Uses ConcurrentHashMap with BlockingQueues to implement a simple
 * in-process message queue. Messages are delivered to all subscribers
 * within the same JVM.
 * </p>
 */
@Slf4j
public class InMemoryMQProvider implements MessageQueueProvider, AutoCloseable {

    private final Map<String, List<BlockingQueue<CloudMessage>>> topicQueues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "inmemory-mq-delay");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService subscriberPool = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "inmemory-mq-sub");
        t.setDaemon(true);
        return t;
    });

    @Override
    public MessagePublisher createPublisher() {
        return new InMemoryPublisher();
    }

    @Override
    public MessageSubscriber createSubscriber() {
        return new InMemorySubscriber();
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public void close() {
        scheduler.shutdown();
        subscriberPool.shutdown();
        topicQueues.clear();
        log.info("InMemoryMQProvider shut down");
    }

    private BlockingQueue<CloudMessage> getOrCreateQueue(String topic, String group) {
        String key = topic + ":" + group;
        BlockingQueue<CloudMessage> queue = new LinkedBlockingQueue<>();
        topicQueues.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(queue);
        return queue;
    }

    private void dispatch(String topic, CloudMessage message) {
        // Deliver to all subscriber groups for this topic
        for (Map.Entry<String, List<BlockingQueue<CloudMessage>>> entry : topicQueues.entrySet()) {
            if (entry.getKey().startsWith(topic + ":")) {
                for (BlockingQueue<CloudMessage> queue : entry.getValue()) {
                    queue.offer(message);
                }
            }
        }
    }

    /**
     * In-memory message publisher.
     */
    private class InMemoryPublisher implements MessagePublisher {

        @Override
        public void publish(String topic, CloudMessage message) {
            log.debug("Publishing message to topic: {}, id: {}", topic, message.getId());
            dispatch(topic, message);
        }

        @Override
        public void publishDelayed(String topic, CloudMessage message, Duration delay) {
            log.debug("Scheduling delayed message to topic: {}, delay: {}", topic, delay);
            scheduler.schedule(() -> dispatch(topic, message), delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * In-memory message subscriber.
     */
    private class InMemorySubscriber implements MessageSubscriber {

        @Override
        public void subscribe(String topic, String group, MessageHandler handler) {
            log.info("Subscribing to topic: {}, group: {}", topic, group);
            BlockingQueue<CloudMessage> queue = getOrCreateQueue(topic, group);

            subscriberPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        CloudMessage message = queue.take();
                        log.debug("Delivering message to handler, topic: {}, id: {}", topic, message.getId());
                        handler.handle(message);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("Subscriber thread interrupted for topic: {}", topic);
                    } catch (Exception e) {
                        log.error("Error handling message on topic: {}", topic, e);
                    }
                }
            });
        }

        @Override
        public void unsubscribe(String topic, String group) {
            String key = topic + ":" + group;
            topicQueues.remove(key);
            log.info("Unsubscribed from topic: {}, group: {}", topic, group);
        }
    }
}
