package run.cloudclaw.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableJpaAuditing
@ComponentScan(basePackages = "run.cloudclaw")
@EntityScan(basePackages = "run.cloudclaw")
@EnableJpaRepositories(basePackages = "run.cloudclaw")
public class CloudClawApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudClawApplication.class, args);
    }
}
