package run.cloudclaw.debug.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CloudClaw debug mode.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cloudclaw.debug")
public class DebugProperties {

    /** Whether debug mode is enabled. When disabled, all debug infrastructure is zero-overhead. */
    private boolean enabled = false;

    /** Maximum length for tool call result content in spans. */
    private int maxResultLength = 2000;

    /** Maximum length for prompt/completion content in spans. */
    private int maxPromptLength = 5000;

    /** Auto-cleanup: number of days to retain debug trace data. */
    private int autoCleanupDays = 30;
}
