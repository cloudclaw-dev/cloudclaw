package run.cloudclaw.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the memory module.
 */
@Data
@ConfigurationProperties(prefix = "cloudclaw.memory")
public class MemoryProperties {

    /**
     * The memory engine implementation to use.
     * Supported values: jdbc (default), mem0.
     */
    private String engine = "jdbc";
}
