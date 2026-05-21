package run.cloudclaw.standalone.config;

import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.config.NoOpConfigChangeNotifier;
import run.cloudclaw.mq.spi.MessageQueueProvider;
import run.cloudclaw.standalone.mq.InMemoryMQProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import run.cloudclaw.standalone.cache.CaffeineCacheConfig;

/**
 * Auto-configuration for CloudClaw standalone mode.
 * Activates when cloudclaw.mode=standalone (default).
 */
@AutoConfiguration
@ConditionalOnProperty(name = "cloudclaw.mode", havingValue = "standalone", matchIfMissing = true)
@Slf4j
@Import(CaffeineCacheConfig.class)
public class StandaloneAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cloudclaw.mq.provider", havingValue = "inmemory", matchIfMissing = true)
    public MessageQueueProvider messageQueueProvider() {
        log.info("Initializing InMemoryMQProvider for standalone mode");
        return new InMemoryMQProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ConfigChangeNotifier.class)
    public ConfigChangeNotifier configChangeNotifier() {
        log.info("Initializing NoOpConfigChangeNotifier for standalone mode");
        return new NoOpConfigChangeNotifier();
    }
}
