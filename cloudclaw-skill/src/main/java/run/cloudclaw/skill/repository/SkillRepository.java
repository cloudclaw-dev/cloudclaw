package run.cloudclaw.skill.repository;

import run.cloudclaw.common.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Skill entity access.
 *
 * <p>Provides standard CRUD operations plus custom finders
 * for enabled skills and lookup by name.</p>
 */
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    /**
     * Find all skills that are currently enabled.
     *
     * @return list of enabled skills
     */
    List<Skill> findByEnabledTrue();

    /**
     * Find a skill by its unique name.
     *
     * @param name the skill name to search for
     * @return an Optional containing the skill if found
     */
    Optional<Skill> findByName(String name);
}
