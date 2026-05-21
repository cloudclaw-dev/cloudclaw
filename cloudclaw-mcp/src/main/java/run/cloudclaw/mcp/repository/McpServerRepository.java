package run.cloudclaw.mcp.repository;

import run.cloudclaw.common.model.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MCP server configuration entities.
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServer, UUID> {

    /**
     * Find all enabled MCP servers.
     *
     * @return list of enabled MCP servers
     */
    List<McpServer> findByEnabledTrue();

    /**
     * Find an MCP server by its unique name.
     *
     * @param name the server name
     * @return optional containing the server if found
     */
    Optional<McpServer> findByName(String name);
}
