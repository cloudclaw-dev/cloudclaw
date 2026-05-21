package run.cloudclaw.skill.repository;

import run.cloudclaw.common.model.SkillFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SkillFile entity — stores individual files within a skill directory.
 */
public interface SkillFileRepository extends JpaRepository<SkillFile, UUID> {

    /**
     * List all files belonging to a skill.
     */
    List<SkillFile> findBySkillIdOrderByFilePath(UUID skillId);

    /**
     * Find a specific file by skill and path.
     */
    Optional<SkillFile> findBySkillIdAndFilePath(UUID skillId, String filePath);

    /**
     * Delete all files for a skill.
     */
    void deleteBySkillId(UUID skillId);

    /**
     * Find files under a specific directory prefix (e.g. "scripts/", "references/").
     */
    List<SkillFile> findBySkillIdAndFilePathStartingWith(UUID skillId, String pathPrefix);
}
