package run.cloudclaw.admin.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the in-memory log appender with Logback so that
 * all application logs flow into the ring buffer.
 */
@Configuration
public class LogAppenderConfig {

    @Bean
    public InMemoryLogAppender inMemoryLogAppender() {
        InMemoryLogAppender appender = new InMemoryLogAppender();
        appender.setMaxSize(1000);
        appender.setName("ADMIN_IN_MEMORY");

        // Attach to Logback root logger
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(loggerContext);
        appender.start();

        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);

        return appender;
    }
}
