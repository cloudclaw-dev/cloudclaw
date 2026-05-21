package run.cloudclaw.agent.prompt;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.skill.service.SkillService;
import run.cloudclaw.memory.injector.MemoryInjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Assembles the system prompt following Claude Agent Skill standard.
 *
 * <p>Progressive disclosure — LLM-driven:</p>
 * <ol>
 *   <li>Metadata (name + description) — always in context for LLM matching</li>
 *   <li>Instructions (SKILL.md body) — injected when user message matches</li>
 *   <li>Files (scripts/references/assets) — LLM loads on demand via skill tools</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromptAssembler {

    private final MemoryInjector memoryInjector;
    private final SkillService skillService;

    public String assembleSystemPrompt(AgentConfig config, String userId, String sessionId, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // 1. Base system prompt
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            sb.append(config.getSystemPrompt()).append("\n\n");
        }

        // 1.5 Current date and time
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale.CHINA));
        sb.append("Current date and time: ")
                .append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append(" (").append(dayOfWeek).append(")\n\n");

        // 2. Skills — Progressive disclosure: Level 1 only (metadata)
        // Level 2 (instructions) and Level 3 (files) are loaded by the LLM on-demand
        // via skill_read_file tool calls, not pre-injected into the prompt.
        try {
            List<Skill> agentSkills = skillService.getSkillsForAgent(config.getAgentId());
            if (!agentSkills.isEmpty()) {
                sb.append("## Available Skills\n\n");
                sb.append("You have access to the following skills. Use the skill tools to load instructions and files when needed.\n\n");
                sb.append("### Tools available:\n");
                sb.append("- `skill_read_file(skill_name, file_path)` — Read a file from a skill's directory. Use `'SKILL.md'` as file_path to load the skill's instructions.\n");
                sb.append("- `skill_execute_script(skill_name, script_path, arguments)` — Execute a script from a skill\n\n");
                sb.append("### Skill list:\n");
                for (int i = 0; i < agentSkills.size(); i++) {
                    Skill skill = agentSkills.get(i);
                    sb.append(i + 1).append(". **").append(skill.getName()).append("**");
                    if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                        sb.append(": ").append(skill.getDescription());
                    }
                    sb.append("\n");
                }
                sb.append("\nWhen a user request matches a skill, use `skill_read_file` to load `SKILL.md` first, then follow its instructions.\n\n");
            }
        } catch (Exception e) {
            log.warn("Failed to load skills for agent {}: {}", config.getAgentId(), e.getMessage());
        }

        // 3. Memory context
        try {
            String memoryContext = memoryInjector.buildMemoryContext(userId, sessionId, config.getAgentId(), userMessage);
            if (memoryContext != null && !memoryContext.isBlank()) {
                sb.append(memoryContext);
            }
        } catch (Exception e) {
            log.warn("Failed to build memory context: {}", e.getMessage());
        }

        return sb.toString();
    }
}
