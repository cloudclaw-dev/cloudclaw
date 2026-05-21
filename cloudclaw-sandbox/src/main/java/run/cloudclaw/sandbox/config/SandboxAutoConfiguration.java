package run.cloudclaw.sandbox.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = "run.cloudclaw.sandbox")
@EnableJpaRepositories(basePackages = "run.cloudclaw.sandbox.model")
@EnableConfigurationProperties(SandboxProperties.class)
@Slf4j
public class SandboxAutoConfiguration {

    public SandboxAutoConfiguration() {
        log.info("CloudClaw Sandbox module auto-configuration initialized");
    }
}
