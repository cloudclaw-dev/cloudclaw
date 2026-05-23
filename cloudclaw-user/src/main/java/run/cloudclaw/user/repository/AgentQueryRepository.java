package run.cloudclaw.user.repository;

import run.cloudclaw.common.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for querying Agent entities within the user module.
 *
 * <p>Provides read-only access to agent data needed by user-facing API endpoints.
 * This repository is maintained locally in the user module to keep the user
 * API layer decoupled from the admin-level agent management.</p>
 */
public interface AgentQueryRepository extends JpaRepository<Agent, UUID> {

    /**
     * Find all enabled agents.
     *
     * @return list of agents that are currently enabled
     */
    List<Agent> findByEnabledTrue();
}
