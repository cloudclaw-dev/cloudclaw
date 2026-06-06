package run.cloudclaw.agent.prompt;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.skill.service.SkillService;
import run.cloudclaw.memory.injector.MemoryContext;
import run.cloudclaw.memory.injector.MemoryInjector;
import run.cloudclaw.memory.injector.MemoryRefHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Assembles the system prompt following Claude Agent Skill standard.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromptAssembler {

    /** Memory tool usage guide appended to system prompts when memory tools are enabled. */
    public static final String MEMORY_GUIDE = "\n\n## Memory\n\n" +
            "You have persistent memory via 2 tools. Use them proactively - don't wait to be asked.\n\n" +
            "Core principle: Save only facts that will still matter in future sessions.\n" +
            "The most valuable memory prevents the user from having to repeat themselves.\n\n" +
            "Two targets:\n" +
            "- memory_profile: Who the user IS - name, role, preferences, habits, corrections.\n" +
            "  Persists across all sessions. 1000 token limit.\n" +
            "- memory_session: Current task context - goals, progress, agreements, constraints.\n" +
            "  This session only. 2000 token limit.\n\n" +
            "WHEN TO SAVE (proactive):\n" +
            "- User corrects you or says 'remember this' / 'don't do that again'\n" +
            "- User shares a preference, habit, or personal detail\n" +
            "- User mentions their name, role, timezone, or communication style\n" +
            "Priority: User corrections > preferences > personal facts > communication style.\n\n" +
            "DO NOT save: common knowledge, completed-work logs, temporary TODO state,\n" +
            "or anything that will be stale in 7 days.\n\n" +
            "HOW TO WRITE - declarative facts, not instructions:\n" +
            "OK 'User prefers concise responses'  NO 'Always respond concisely'\n\n" +
            "ACTIONS:\n" +
            "- memory_profile: read_all | add | replace | remove\n" +
            "- memory_session: read_all | add | replace | remove";

    private final MemoryInjector memoryInjector;
    private final SkillService skillService;

    public String assembleSystemPrompt(AgentConfig config, String userId, String sessionId, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // 1. Base system prompt
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            sb.append(config.getSystemPrompt()).append("\n\n");
        }

        // 1.5 Sub-agent list hint (if root agent has sub-agents)
        if (config.getSubAgents() != null && !config.getSubAgents().isEmpty()) {
            sb.append("## Available Sub-Agents\n\n");
            sb.append("You can transfer the conversation to a specialized sub-agent using transfer tools.\n\n");
            for (int i = 0; i < config.getSubAgents().size(); i++) {
                AgentConfig.SubAgentDef sub = config.getSubAgents().get(i);
                sb.append(i + 1).append(". **").append(sub.getDisplayName() != null ? sub.getDisplayName() : sub.getName()).append("**");
                if (sub.getDescription() != null && !sub.getDescription().isBlank()) {
                    sb.append(": ").append(sub.getDescription());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 1.6 Current date and time
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale.CHINA));
        sb.append("Current date and time: ")
                .append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append(" (").append(dayOfWeek).append(")\n\n");

        // 2. Skills — Progressive disclosure: Level 1 only (metadata)
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
            String memoryContext = MemoryRefHolder.captureAndReturnContent(memoryInjector.buildMemoryContextWithRefs(userId, sessionId, 3000));
            if (memoryContext != null && !memoryContext.isBlank()) {
                sb.append(memoryContext);
            }
        } catch (Exception e) {
            log.warn("Failed to build memory context: {}", e.getMessage());
        }

        return sb.toString();
    }

    /**
     * Assemble system prompt for a sub-agent.
     * Uses the sub-agent's system prompt as base, with root agent's skills and memory.
     */
    public String assembleSubAgentSystemPrompt(ResolvedAgent subAgent, AgentConfig rootConfig,
                                                 String userId, String sessionId, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // 1. Sub-agent's own system prompt
        if (subAgent.getSystemPrompt() != null && !subAgent.getSystemPrompt().isBlank()) {
            sb.append(subAgent.getSystemPrompt()).append("\n\n");
        }

        // 2. Current date and time
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE", java.util.Locale.CHINA));
        sb.append("Current date and time: ")
                .append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append(" (").append(dayOfWeek).append(")\n\n");

        // 3. Skills for the sub-agent (by skillIds)
        if (subAgent.getSkillIds() != null && !subAgent.getSkillIds().isEmpty()) {
            try {
                List<Skill> agentSkills = skillService.getSkillsForAgent(rootConfig.getAgentId());
                // Filter to sub-agent's skills
                List<Skill> subSkills = agentSkills.stream()
                        .filter(s -> subAgent.getSkillIds().contains(s.getId().toString()))
                        .toList();
                if (!subSkills.isEmpty()) {
                    sb.append("## Available Skills\n\n");
                    for (int i = 0; i < subSkills.size(); i++) {
                        Skill skill = subSkills.get(i);
                        sb.append(i + 1).append(". **").append(skill.getName()).append("**");
                        if (skill.getDescription() != null) {
                            sb.append(": ").append(skill.getDescription());
                        }
                        sb.append("\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to load sub-agent skills: {}", e.getMessage());
            }
        }

        // 4. Memory context (shared from root)
        try {
            String memoryContext = MemoryRefHolder.captureAndReturnContent(memoryInjector.buildMemoryContextWithRefs(userId, sessionId, 3000));
            if (memoryContext != null && !memoryContext.isBlank()) {
                sb.append(memoryContext);
            }
        } catch (Exception e) {
            log.warn("Failed to build memory context: {}", e.getMessage());
        }

        return sb.toString();
    }
}
