package run.cloudclaw.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Auto-configuration for the CloudClaw Agent module.
 * <p>
 * Scans and registers all agent-related components including:
 * <ul>
 *   <li>AgentConfigService - agent configuration loading and caching</li>
 *   <li>ChatEngine - core chat engine with Spring AI integration</li>
 *   <li>PromptAssembler - system prompt assembly with memory and skills</li>
 *   <li>AgentRepository and related JPA repositories</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "run.cloudclaw.agent")
@Slf4j
public class AgentAutoConfiguration {

    public AgentAutoConfiguration() {
        log.info("CloudClaw Agent module auto-configuration initialized");
    }

    @Bean("chatExecutor")
    public Executor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        executor.getThreadPoolExecutor().allowCoreThreadTimeOut(true);
        log.info("Chat async executor initialized: core=4, max=16, queue=100");
        return executor;
    }

    @Bean("workflowExecutor")
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("workflow-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        // Allow core threads to time out so the pool can shrink when idle
        executor.getThreadPoolExecutor().allowCoreThreadTimeOut(true);
        log.info("Workflow executor initialized: core=4, max=16, queue=200");
        return executor;
    }

    @Bean("parallelTaskExecutor")
    public Executor parallelTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("parallel-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Parallel task executor initialized: core=2, max=8, queue=50");
        return executor;
    }

    @Bean("asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        log.info("Async task executor initialized: core=2, max=4, queue=200");
        return executor;
    }
}
