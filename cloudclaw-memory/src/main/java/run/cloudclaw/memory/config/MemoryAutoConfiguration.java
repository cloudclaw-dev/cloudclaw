package run.cloudclaw.memory.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the CloudClaw memory module.
 * Sets up repositories, service, and injector components.
 */
@AutoConfiguration
@EnableConfigurationProperties({MemoryProperties.class})
@ComponentScan(basePackages = "run.cloudclaw.memory")
@Slf4j
public class MemoryAutoConfiguration {

    public MemoryAutoConfiguration(MemoryProperties memoryProperties) {
        log.info("CloudClaw Memory module initialized with engine: {}", memoryProperties.getEngine());
    }
}
