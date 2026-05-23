package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Admin repository for Agent entity access.
 * Provides standard CRUD operations for agent management.
 */
public interface AdminAgentRepository extends JpaRepository<Agent, UUID> {
}
