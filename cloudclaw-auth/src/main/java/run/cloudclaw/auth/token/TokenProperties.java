package run.cloudclaw.auth.token;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT token configuration properties.
 *
 * <p>Prefix: {@code cloudclaw.jwt}</p>
 */
@Data
@ConfigurationProperties(prefix = "cloudclaw.jwt")
public class TokenProperties {

    /**
     * Secret key for signing JWT tokens. Must be at least 256 bits (32 bytes) for HS256.
     */
    private String secret;

    /**
     * Access token time-to-live. Defaults to 2 hours.
     */
    private Duration accessTokenTtl = Duration.ofHours(2);

    /**
     * Refresh token time-to-live. Defaults to 7 days.
     */
    private Duration refreshTokenTtl = Duration.ofDays(7);
}
