package run.cloudclaw.mcp.repository;

import run.cloudclaw.common.model.AgentMcpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for agent-to-MCP-server binding entities.
 */
@Repository
public interface AgentMcpServerRepository extends JpaRepository<AgentMcpServer, AgentMcpServer.AgentMcpServerId> {

    List<AgentMcpServer> findByAgentId(UUID agentId);
}
