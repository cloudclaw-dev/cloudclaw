package run.cloudclaw.mcp.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration class for the CloudClaw MCP module.
 * <p>
 * Enables the MCP configuration properties and registers all
 * MCP-related components (connection pool, router, permission checker, etc.)
 * via component scanning.
 * <p>
 * Registered via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * for automatic discovery by Spring Boot.
 */
@AutoConfiguration
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
    // Components are discovered via @Component/@Service annotations
    // in the run.cloudclaw.mcp package through component scanning.
}
