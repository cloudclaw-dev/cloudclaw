package run.cloudclaw.agent.tools;

import run.cloudclaw.common.model.Skill;
import run.cloudclaw.common.model.SkillFile;
import run.cloudclaw.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Skill tools using Spring AI standard @Tool annotations.
 * Registered as a Spring bean for auto-discovery by ToolCallingManager.
 *
 * Progressive disclosure:
 * - Level 1: Skill metadata (always in system prompt)
 * - Level 2: Instructions (injected when matched)
 * - Level 3: Files & scripts (LLM-driven via these tools)
 */
@Slf4j
@Component
public class SkillTools {

    private final SkillService skillService;

    public SkillTools(SkillService skillService) {
        this.skillService = skillService;
    }

    @Tool(description = "Read a file from a skill's directory. Use this to load reference docs, API guides, config files, or any file attached to a skill.")
    public String readFile(
            @ToolParam(description = "The name of the skill") String skill_name,
            @ToolParam(description = "Path of the file to read, e.g. 'references/api-guide.md' or 'scripts/weather.py'") String file_path) {
        try {
            log.info("skill_read_file: skill={}, path={}", skill_name, file_path);

            List<Skill> skills = skillService.listSkills();
            Skill target = skills.stream()
                    .filter(s -> s.getName().equals(skill_name))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                return "Error: Skill '" + skill_name + "' not found. Available: "
                        + skills.stream().map(Skill::getName).toList();
            }

            SkillFile file = skillService.getFile(target.getId().toString(), file_path);
            if (file == null || file.getContent() == null) {
                return "Error: File '" + file_path + "' not found in skill '" + skill_name + "'";
            }

            return file.getContent();
        } catch (Exception e) {
            log.error("skill_read_file error: {}", e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Execute a script from a skill's scripts/ directory. The script will be run and its stdout returned. Supports Python, Shell, JavaScript, and Ruby scripts.")
    public String executeScript(
            @ToolParam(description = "The name of the skill") String skill_name,
            @ToolParam(description = "Path of the script to execute, e.g. 'scripts/weather.py'") String script_path,
            @ToolParam(description = "Command-line arguments to pass to the script (space-separated)", required = false) String arguments) {
        try {
            log.info("skill_execute_script: skill={}, script={}, args={}", skill_name, script_path, arguments);

            List<Skill> skills = skillService.listSkills();
            Skill target = skills.stream()
                    .filter(s -> s.getName().equals(skill_name))
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                return "Error: Skill '" + skill_name + "' not found.";
            }

            SkillFile scriptFile = skillService.getFile(target.getId().toString(), script_path);
            if (scriptFile == null || scriptFile.getContent() == null) {
                return "Error: Script '" + script_path + "' not found in skill '" + skill_name + "'";
            }

            String ext = script_path.substring(script_path.lastIndexOf('.') + 1).toLowerCase();
            String interpreter = switch (ext) {
                case "py" -> "python";
                case "sh", "bash" -> "bash";
                case "js" -> "node";
                case "rb" -> "ruby";
                default -> null;
            };

            if (interpreter == null) {
                return "Error: Unsupported script type '." + ext + "'. Supported: .py, .sh, .js, .rb";
            }

            java.nio.file.Path tempScript = java.nio.file.Files.createTempFile("cloudclaw-skill-", "." + ext);
            try {
                java.nio.file.Files.writeString(tempScript, scriptFile.getContent());

                // Validate arguments — reject obviously dangerous patterns
                ProcessBuilder pb = new ProcessBuilder(interpreter, tempScript.toAbsolutePath().toString());
                if (arguments != null && !arguments.isBlank()) {
                    String[] args = arguments.split("\\s+");
                    for (String arg : args) {
                        if (arg.contains("..") || arg.startsWith("/") || arg.startsWith("~")) {
                            return "Error: Invalid argument: '" + arg + "'";
                        }
                    }
                    pb.command().addAll(List.of(args));
                }
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Script timed out after 30 seconds.\nPartial output:\n" + output;
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    return "Script exited with code " + exitCode + ".\nOutput:\n" + output;
                }

                return output;
            } finally {
                java.nio.file.Files.deleteIfExists(tempScript);
            }
        } catch (Exception e) {
            log.error("skill_execute_script error: {}", e.getMessage());
            return "Error executing script: " + e.getMessage();
        }
    }
}
