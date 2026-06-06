package run.cloudclaw.debug.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import run.cloudclaw.debug.handler.CloudClawTraceHandler;
import run.cloudclaw.debug.recorder.CustomSpanRecorder;

import java.util.concurrent.Executor;

/**
 * Auto-configuration for the CloudClaw Debug module.
 *
 * <p>When cloudclaw.debug.enabled=true, registers all debug infrastructure:
 * observation handler, trace repository, event bus, and debug API controller.</p>
 *
 * <p>When cloudclaw.debug.enabled=false (default), only the DebugProperties bean
 * and no-op CustomSpanRecorder are registered for zero overhead.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "run.cloudclaw.debug")
@EnableConfigurationProperties(DebugProperties.class)
@Slf4j
public class DebugAutoConfiguration {

    public DebugAutoConfiguration(DebugProperties props) {
        if (props.isEnabled()) {
            log.info("CloudClaw Debug module enabled - trace observation active");
        } else {
            log.info("CloudClaw Debug module disabled - zero overhead mode");
        }
    }

    @Bean("debugTaskExecutor")
    @ConditionalOnProperty(name = "cloudclaw.debug.enabled", havingValue = "true")
    public Executor debugTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("debug-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * Initialize CustomSpanRecorder with the trace handler when debug is enabled.
     */
    @Bean
    @ConditionalOnProperty(name = "cloudclaw.debug.enabled", havingValue = "true")
    public CustomSpanRecorderInitializer customSpanRecorderInitializer(DebugProperties props,
                                                                         CloudClawTraceHandler traceHandler) {
        CustomSpanRecorder.init(true, traceHandler);
        return new CustomSpanRecorderInitializer();
    }

    /**
     * When debug is disabled, initialize CustomSpanRecorder in no-op mode.
     */
    @Bean
    @ConditionalOnProperty(name = "cloudclaw.debug.enabled", havingValue = "false", matchIfMissing = true)
    public NoOpCustomSpanRecorderInitializer noOpCustomSpanRecorderInitializer() {
        CustomSpanRecorder.init(false, null);
        return new NoOpCustomSpanRecorderInitializer();
    }

    /** Marker bean for conditional initialization. */
    public static class CustomSpanRecorderInitializer {}

    /** Marker bean for no-op initialization. */
    public static class NoOpCustomSpanRecorderInitializer {}
}
