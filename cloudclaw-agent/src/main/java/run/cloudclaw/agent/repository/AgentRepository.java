package run.cloudclaw.agent.repository;

import run.cloudclaw.common.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for accessing Agent entities.
 */
@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    /**
     * Find all agents that are currently enabled.
     *
     * @return list of enabled agents
     */
    List<Agent> findByEnabledTrue();
}
