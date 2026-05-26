package run.cloudclaw.app;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync
@EnableJpaAuditing
@ComponentScan(basePackages = "run.cloudclaw")
@EntityScan(basePackages = "run.cloudclaw")
@EnableJpaRepositories(basePackages = "run.cloudclaw")
public class CloudClawApplication implements AsyncConfigurer {

    private final Executor asyncTaskExecutor;

    public CloudClawApplication(@Qualifier("asyncTaskExecutor") Executor asyncTaskExecutor) {
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncTaskExecutor;
    }

    public static void main(String[] args) {
        SpringApplication.run(CloudClawApplication.class, args);
    }
}
