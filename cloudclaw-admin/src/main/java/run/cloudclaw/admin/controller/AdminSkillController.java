package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.repository.AdminSkillRepository;
import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.common.model.SkillFile;
import run.cloudclaw.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/skills")
@RequiredArgsConstructor
public class AdminSkillController {

    private final AdminSkillRepository skillRepository;
    private final SkillService skillService;
    private final ConfigChangeNotifier configChangeNotifier;

    // ========== Skill CRUD ==========

    /**
     * Upload a skill from a ZIP file.
     * The ZIP must contain SKILL.md at its root (or one directory level deep).
     */
    @PostMapping("/upload")
    public Result<Skill> uploadSkill(@RequestParam("file") MultipartFile file) {
        log.info("Admin uploading skill from file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        // Validate file size (max 50MB)
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.SKILL_FILE_TOO_LARGE);
        }

        // Validate file type
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.SKILL_INVALID_PACKAGE);
        }

        Skill skill = skillService.uploadSkillZip(file);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "skill", skill.getId().toString());
        return Result.ok(skill);
    }

    @GetMapping
    public Result<List<Skill>> listSkills() {
        return Result.ok(skillService.listAllSkills());
    }

    @PutMapping("/{id}")
    public Result<Skill> updateSkill(@PathVariable String id,
                                     @RequestBody Map<String, Object> updates) {
        log.info("Admin updating skill: {}", id);
        String name = updates.get("name") != null ? updates.get("name").toString() : null;
        String description = updates.get("description") != null ? updates.get("description").toString() : null;
        Boolean enabled = updates.get("enabled") != null ? Boolean.valueOf(updates.get("enabled").toString()) : null;
        Skill skill = skillService.updateSkillMetadata(id, name, description, enabled);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "skill", id);
        return Result.ok(skill);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSkill(@PathVariable String id) {
        log.info("Admin deleting skill: {}", id);
        skillService.deleteSkill(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "skill", id);
        return Result.ok();
    }

    // ========== File Management ==========

    /**
     * List all files in a skill directory.
     */
    @GetMapping("/{id}/files")
    public Result<List<SkillFile>> listFiles(@PathVariable String id) {
        return Result.ok(skillService.listFiles(id));
    }

    /**
     * Get a specific file's content.
     */
    @GetMapping("/{id}/files/**")
    public Result<SkillFile> getFile(@PathVariable String id,
                                     @org.springframework.web.bind.annotation.RequestParam(required = false) String path) {
        // Extract the file path from the request URI
        // Using query param 'path' as a fallback since /** doesn't work well in all cases
        if (path == null || path.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File path is required");
        }
        return Result.ok(skillService.getFile(id, path));
    }

    /**
     * Create or update a file in the skill.
     */
    @PutMapping("/{id}/files")
    public Result<SkillFile> saveFile(@PathVariable String id,
                                      @RequestBody Map<String, String> body) {
        String filePath = body.get("path");
        String content = body.get("content");
        if (filePath == null || filePath.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File path is required");
        }
        try {
            return Result.ok(skillService.updateFile(id, filePath, content));
        } catch (BusinessException e) {
            // File doesn't exist, create it
            return Result.ok(skillService.createFile(id, filePath, content));
        }
    }

    /**
     * Delete a file from the skill.
     */
    @DeleteMapping("/{id}/files")
    public Result<Void> deleteFile(@PathVariable String id,
                                   @RequestParam String path) {
        skillService.deleteFile(id, path);
        return Result.ok();
    }
}
