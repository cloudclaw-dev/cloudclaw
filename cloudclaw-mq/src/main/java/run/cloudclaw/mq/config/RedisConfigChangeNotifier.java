package run.cloudclaw.mq.config;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 Redis Pub/Sub 的配置变更通知器。
 * <p>
 * 同时承担发布者和订阅者角色：
 * <ul>
 *   <li>发布：管理 API 修改配置后，publish 到对应 channel</li>
 *   <li>订阅：收到消息后清除 CacheManager 缓存 + 发布 Spring 本地事件</li>
 * </ul>
 * 幂等设计：发送方自己也会收到消息，但清除缓存是幂等操作。
 * </p>
 */
@Slf4j
public class RedisConfigChangeNotifier implements ConfigChangeNotifier, MessageListener {

    /** channel 前缀 */
    private static final String CHANNEL_PREFIX = "cloudclaw:config:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private ApplicationEventPublisher eventPublisher;

    public RedisConfigChangeNotifier(RedisTemplate<String, Object> redisTemplate,
                                     ObjectMapper objectMapper,
                                     CacheManager cacheManager) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * 设置 Spring 事件发布器（由配置类在 bean 初始化后注入）。
     */
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ========== 发布端 ==========

    @Override
    public void notifyChange(ConfigChangeEvent.ChangeType changeType, String entityType, String entityId) {
        ConfigChangeEvent event = new ConfigChangeEvent(changeType, entityType, entityId);
        String channel = CHANNEL_PREFIX + entityType;

        try {
            String message = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, message);
            log.info("发布配置变更通知: channel={}, event={}", channel, event);
        } catch (Exception e) {
            log.error("发布配置变更通知失败: channel={}, event={}", channel, event, e);
        }
    }

    // ========== 订阅端 ==========

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            ConfigChangeEvent event = objectMapper.readValue(body, ConfigChangeEvent.class);
            log.info("收到配置变更通知: channel={}, event={}", channel, event);

            // 1. 清除 CacheManager 中的缓存
            evictCache(event);

            // 2. 发布 Spring 本地事件，让各模块监听并执行自己的刷新逻辑
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }

        } catch (Exception e) {
            log.error("处理配置变更通知失败: channel={}, body={}", channel, body, e);
        }
    }

    /**
     * 清除 CacheManager 中对应实体的缓存。
     */
    private void evictCache(ConfigChangeEvent event) {
        if ("agent".equals(event.entityType())) {
            Cache cache = cacheManager.getCache("agentConfig");
            if (cache != null) {
                cache.evict(event.entityId());
                log.info("已清除 agentConfig 缓存: entityId={}", event.entityId());
            }
        }
        // skill / mcp / llm 的缓存通过 Spring 事件由各模块自行处理
    }
}
