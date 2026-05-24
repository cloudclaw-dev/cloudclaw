package run.cloudclaw.skill.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.AgentSkill;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.common.model.SkillFile;
import run.cloudclaw.skill.repository.AgentSkillRepository;
import run.cloudclaw.skill.repository.SkillFileRepository;
import run.cloudclaw.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for skill management following the Claude Agent Skill standard.
 *
 * <p>Skills are managed as directory trees (SKILL.md + scripts/ + references/ + assets/)
 * but stored in DB for stateless deployment.</p>
 *
 * <p>Three-level progressive disclosure:</p>
 * <ol>
 *   <li>Metadata (name + description) — always in context for LLM matching</li>
 *   <li>Instructions (SKILL.md body) — loaded when skill triggers</li>
 *   <li>Files (scripts/references/assets) — loaded on demand</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final SkillFileRepository skillFileRepository;

    // ========== CRUD ==========

    @Transactional(readOnly = true)
    public List<Skill> listSkills() {
        return skillRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public List<Skill> listAllSkills() {
        return skillRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Skill getSkill(String skillId) {
        UUID id = UUID.fromString(skillId);
        return skillRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKILL_NOT_FOUND, skillId));
    }

    @Transactional
    public void deleteSkill(String skillId) {
        Skill skill = getSkill(skillId);
        skillFileRepository.deleteBySkillId(skill.getId());
        skillRepository.delete(skill);
        log.info("Deleted skill: {} ({})", skill.getName(), skillId);
    }

    @Transactional
    public Skill updateSkillMetadata(String skillId, String name, String description, Boolean enabled) {
        Skill skill = getSkill(skillId);
        if (name != null) skill.setName(name);
        if (description != null) skill.setDescription(description);
        if (enabled != null) skill.setEnabled(enabled);
        return skillRepository.save(skill);
    }

    // ========== Upload & Parse ==========

    /**
     * Upload a skill from a ZIP file. The ZIP should contain a skill directory
     * with at least a SKILL.md file at its root (or one level deep).
     *
     * <p>ZIP structure examples:</p>
     * <pre>
     * my-skill.zip
     * └── my-skill/
     *     ├── SKILL.md
     *     ├── scripts/rotate.py
     *     └── references/api.md
     *
     * OR flat:
     * my-skill.zip
     * ├── SKILL.md
     * ├── scripts/rotate.py
     * └── references/api.md
     * </pre>
     *
     * @param zipFile the uploaded ZIP file
     * @return the created/replaced skill
     */
    @Transactional
    public Skill uploadSkillZip(MultipartFile zipFile) {
        String filename = zipFile.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".zip"))) {
            throw new BusinessException(ErrorCode.SKILL_INVALID_PACKAGE);
        }

        // Derive skill name from filename: "my-skill.zip" → "my-skill"
        String skillName = filename.replaceAll("\\.zip$", "");

        try {
            List<FileEntry> entries = parseZip(zipFile.getBytes());
            if (entries.isEmpty()) {
                throw new BusinessException(ErrorCode.SKILL_INVALID_PACKAGE, "ZIP is empty");
            }

            // Find SKILL.md
            FileEntry skillMd = findSkillMd(entries);
            if (skillMd == null) {
                throw new BusinessException(ErrorCode.SKILL_INVALID_PACKAGE, "SKILL.md not found in ZIP");
            }

            // Parse SKILL.md: frontmatter + body
            ParsedSkillMd parsed = parseSkillMd(skillMd.content);
            if (parsed.name != null && !parsed.name.isBlank()) {
                skillName = parsed.name;
            }

            // Create or replace skill
            Skill skill = skillRepository.findByName(skillName)
                    .orElseGet(Skill::new);
            skill.setName(skillName);
            skill.setDescription(parsed.description != null ? parsed.description : "");
            skill.setInstructions(parsed.instructions != null ? parsed.instructions : "");
            skill.setEnabled(true);

            // Remove old files if replacing
            if (skill.getId() != null) {
                skillFileRepository.deleteBySkillId(skill.getId());
            }

            skill = skillRepository.save(skill);

            // Save all files
            for (FileEntry entry : entries) {
                // Skip SKILL.md content (already parsed into skill metadata)
                // but still save it as a file for browsing
                SkillFile sf = new SkillFile();
                sf.setSkill(skill);
                sf.setFilePath(entry.path);
                sf.setContent(entry.content);
                sf.setFileType(guessFileType(entry.path));
                skillFileRepository.save(sf);
            }

            log.info("Uploaded skill '{}' with {} files", skillName, entries.size());
            return skill;

        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SKILL_INVALID_PACKAGE, "Failed to read ZIP: " + e.getMessage());
        }
    }

    // ========== File Operations ==========

    @Transactional(readOnly = true)
    public List<SkillFile> listFiles(String skillId) {
        UUID id = UUID.fromString(skillId);
        return skillFileRepository.findBySkillIdOrderByFilePath(id);
    }

    @Transactional(readOnly = true)
    public SkillFile getFile(String skillId, String filePath) {
        UUID id = UUID.fromString(skillId);
        return skillFileRepository.findBySkillIdAndFilePath(id, filePath)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, filePath));
    }

    @Transactional
    public SkillFile updateFile(String skillId, String filePath, String content) {
        SkillFile file = getFile(skillId, filePath);
        file.setContent(content);
        return skillFileRepository.save(file);
    }

    @Transactional
    public SkillFile createFile(String skillId, String filePath, String content) {
        UUID id = UUID.fromString(skillId);
        // Check if file already exists
        skillFileRepository.findBySkillIdAndFilePath(id, filePath)
                .ifPresent(f -> { throw new BusinessException(ErrorCode.FILE_ALREADY_EXISTS, filePath); });

        Skill skill = getSkill(skillId);
        SkillFile sf = new SkillFile();
        sf.setSkill(skill);
        sf.setFilePath(filePath);
        sf.setContent(content);
        sf.setFileType(guessFileType(filePath));
        return skillFileRepository.save(sf);
    }

    @Transactional
    public void deleteFile(String skillId, String filePath) {
        SkillFile file = getFile(skillId, filePath);
        skillFileRepository.delete(file);
    }

    // ========== Agent Binding ==========

    @Transactional(readOnly = true)
    public List<Skill> getSkillsForAgent(String agentId) {
        UUID agentUuid = UUID.fromString(agentId);
        List<AgentSkill> agentSkills = agentSkillRepository.findByAgentId(agentUuid);
        List<Skill> skills = new ArrayList<>();
        for (AgentSkill as : agentSkills) {
            skillRepository.findById(as.getSkillId()).ifPresent(skills::add);
        }
        return skills;
    }

    /**
     * Load a reference file from a skill by file path.
     */
    @Transactional(readOnly = true)
    public String loadFileContent(String skillId, String filePath) {
        try {
            return getFile(skillId, filePath).getContent();
        } catch (Exception e) {
            log.warn("Failed to load file '{}' for skill {}: {}", filePath, skillId, e.getMessage());
            return null;
        }
    }

    // ========== Internal Helpers ==========

    private List<FileEntry> parseZip(byte[] zipBytes) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        String commonPrefix = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                // Normalize path separators
                name = name.replace('\\', '/');

                // Skip hidden files and __MACOSX
                if (name.contains("__MACOSX") || name.startsWith(".")) continue;

                // Detect common directory prefix
                int slashIdx = name.indexOf('/');
                if (slashIdx > 0) {
                    String prefix = name.substring(0, slashIdx + 1);
                    if (commonPrefix == null) commonPrefix = prefix;
                    else if (!commonPrefix.equals(prefix)) commonPrefix = "";
                } else {
                    commonPrefix = "";
                }

                // Read content
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String content = baos.toString(StandardCharsets.UTF_8);
                entries.add(new FileEntry(name, content));
            }
        }

        // Strip common prefix
        if (commonPrefix != null && !commonPrefix.isEmpty()) {
            String prefix = commonPrefix;
            for (FileEntry e : entries) {
                e.path = e.path.substring(prefix.length());
            }
        }

        return entries;
    }

    private FileEntry findSkillMd(List<FileEntry> entries) {
        for (FileEntry e : entries) {
            if (e.path.equals("SKILL.md") || e.path.endsWith("/SKILL.md")) {
                return e;
            }
        }
        return null;
    }

    /**
     * Parse SKILL.md with optional YAML frontmatter.
     *
     * <pre>
     * ---
     * name: my-skill
     * description: Does something useful
     * ---
     * # Instructions
     * ...
     * </pre>
     */
    private ParsedSkillMd parseSkillMd(String content) {
        ParsedSkillMd result = new ParsedSkillMd();

        if (content == null || content.isBlank()) return result;

        content = content.trim();

        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx).trim();
                String body = content.substring(endIdx + 3).trim();

                // Simple YAML parsing (no library needed for flat key:value)
                for (String line : frontmatter.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("name:")) {
                        result.name = line.substring(5).trim().replaceAll("^['\"]|['\"]$", "");
                    } else if (line.startsWith("description:")) {
                        result.description = line.substring(12).trim().replaceAll("^['\"]|['\"]$", "");
                    }
                }
                result.instructions = body;
            } else {
                result.instructions = content;
            }
        } else {
            result.instructions = content;
        }

        return result;
    }

    private String guessFileType(String path) {
        if (path == null) return "text";
        String lower = path.toLowerCase();
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "javascript";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "shell";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        return "text";
    }

    // ========== Inner classes ==========

    private static class FileEntry {
        String path;
        String content;

        FileEntry(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private static class ParsedSkillMd {
        String name;
        String description;
        String instructions;
    }
}
