package run.cloudclaw.session.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for session caching.
 *
 * <p>Prefix: {@code cloudclaw.cache}</p>
 */
@Data
@ConfigurationProperties(prefix = "cloudclaw.cache")
public class SessionCacheProperties {

    /**
     * Time-to-live for cached session context and session objects.
     * Defaults to 30 minutes.
     */
    private Duration sessionTtl = Duration.ofMinutes(30);
}
