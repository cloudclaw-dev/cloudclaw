package run.cloudclaw.common.config;

import lombok.extern.slf4j.Slf4j;

/**
 * No-op 配置变更通知器，用于 standalone 模式。
 * 单进程部署不需要跨实例通知，仅打印调试日志。
 */
@Slf4j
public class NoOpConfigChangeNotifier implements ConfigChangeNotifier {

    @Override
    public void notifyChange(ConfigChangeEvent.ChangeType changeType, String entityType, String entityId) {
        log.debug("Standalone mode: config change ignored ({}/{}/{})", changeType, entityType, entityId);
    }
}
