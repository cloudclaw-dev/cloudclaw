package run.cloudclaw.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the MCP module.
 * <p>
 * Configured via the "cloudclaw.mcp" prefix in application.yml.
 *
 * <pre>
 * cloudclaw:
 *   mcp:
 *     pool:
 *       max-connections-per-server: 5
 *       idle-timeout: 300s
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cloudclaw.mcp")
public class McpProperties {

    /**
     * Connection pool settings.
     */
    private Pool pool = new Pool();

    /**
     * Connection pool configuration.
     */
    @Getter
    @Setter
    public static class Pool {

        /**
         * Maximum number of connections per MCP server.
         * Currently the pool maintains one connection per server;
         * this setting is reserved for future connection pooling expansion.
         */
        private int maxConnectionsPerServer = 5;

        /**
         * Idle timeout for connections in the pool.
         * Connections idle longer than this duration may be evicted.
         */
        private Duration idleTimeout = Duration.ofSeconds(300);
    }
}
