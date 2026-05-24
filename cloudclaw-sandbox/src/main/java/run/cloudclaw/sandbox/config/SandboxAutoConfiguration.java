package run.cloudclaw.sandbox.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "run.cloudclaw.sandbox")
@EnableConfigurationProperties(SandboxProperties.class)
@Slf4j
public class SandboxAutoConfiguration {

    public SandboxAutoConfiguration() {
        log.info("CloudClaw Sandbox module auto-configuration initialized");
    }
}
