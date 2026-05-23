package run.cloudclaw.skill.repository;

import run.cloudclaw.common.model.AgentSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for AgentSkill entity access.
 *
 * <p>Manages the many-to-many relationship between agents and skills.
 * Uses composite primary key (agentId, skillId) via AgentSkillId.</p>
 */
public interface AgentSkillRepository extends JpaRepository<AgentSkill, UUID> {

    /**
     * Find all skill associations for a given agent.
     *
     * @param agentId the agent's unique identifier
     * @return list of AgentSkill entries linking the agent to its skills
     */
    List<AgentSkill> findByAgentId(UUID agentId);
}
