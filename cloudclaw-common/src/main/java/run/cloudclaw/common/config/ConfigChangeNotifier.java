package run.cloudclaw.common.config;

/**
 * 配置变更通知器接口。
 * <p>
 * 在 cluster 模式下通过 Redis Pub/Sub 通知所有实例刷新缓存；
 * standalone 模式下为 no-op 实现（单进程无需通知）。
 * </p>
 */
public interface ConfigChangeNotifier {

    /**
     * 发布配置变更通知。
     *
     * @param changeType 变更类型
     * @param entityType 实体类型（skill / agent / mcp / llm）
     * @param entityId   实体 ID
     */
    void notifyChange(ConfigChangeEvent.ChangeType changeType, String entityType, String entityId);
}
