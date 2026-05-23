package run.cloudclaw.common.config;

/**
 * 配置变更事件
 *
 * @param changeType 变更类型：CREATE / UPDATE / DELETE
 * @param entityType 实体类型：skill / agent / mcp / llm
 * @param entityId   实体 ID
 */
public record ConfigChangeEvent(
        ChangeType changeType,
        String entityType,
        String entityId
) {
    public enum ChangeType {
        CREATE, UPDATE, DELETE
    }
}
