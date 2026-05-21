package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Admin repository for Skill entity access.
 * Provides standard CRUD operations for skill management.
 */
public interface AdminSkillRepository extends JpaRepository<Skill, UUID> {
}
