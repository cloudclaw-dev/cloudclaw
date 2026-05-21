package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Admin repository for McpServer entity access.
 * Provides standard CRUD operations for MCP server management.
 */
public interface AdminMcpServerRepository extends JpaRepository<McpServer, UUID> {

    /**
     * Find all enabled MCP servers.
     *
     * @return list of enabled MCP servers
     */
    List<McpServer> findByEnabledTrue();
}
