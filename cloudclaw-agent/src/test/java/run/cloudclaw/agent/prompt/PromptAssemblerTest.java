package run.cloudclaw.agent.prompt;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.memory.injector.MemoryInjector;
import run.cloudclaw.skill.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptAssemblerTest {

    @Mock private MemoryInjector memoryInjector;
    @Mock private SkillService skillService;

    private PromptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PromptAssembler(memoryInjector, skillService);
    }

    private AgentConfig createConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId(UUID.randomUUID().toString());
        config.setName("TestAgent");
        config.setSystemPrompt("You are a helpful assistant.");
        config.setModelId("gpt-4");
        return config;
    }

    @Nested
    @DisplayName("assembleSystemPrompt - basic prompt")
    class BasicPrompt {

        @Test
        @DisplayName("Should include base system prompt")
        void includeBasePrompt() {
            AgentConfig config = createConfig();
            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertTrue(result.startsWith("You are a helpful assistant."));
        }

        @Test
        @DisplayName("Should handle null system prompt")
        void nullSystemPrompt() {
            AgentConfig config = createConfig();
            config.setSystemPrompt(null);

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertFalse(result.startsWith("null"));
        }

        @Test
        @DisplayName("Should handle blank system prompt")
        void blankSystemPrompt() {
            AgentConfig config = createConfig();
            config.setSystemPrompt("   ");

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("assembleSystemPrompt - date/time")
    class DateTime {

        @Test
        @DisplayName("Should include current date and time in Shanghai timezone")
        void includeDateTime() {
            AgentConfig config = createConfig();
            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertTrue(result.contains("Current date and time:"));

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            String expectedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            assertTrue(result.contains(expectedDate));
        }

        @Test
        @DisplayName("Should include day of week in Chinese")
        void includeDayOfWeek() {
            AgentConfig config = createConfig();
            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA));
            assertTrue(result.contains(dayOfWeek));
        }
    }

    @Nested
    @DisplayName("assembleSystemPrompt - sub-agent list")
    class SubAgentList {

        @Test
        @DisplayName("Should include sub-agent list when subAgents is not empty")
        void includeSubAgents() {
            AgentConfig config = createConfig();
            AgentConfig.SubAgentDef sub1 = new AgentConfig.SubAgentDef();
            sub1.setName("TravelBot");
            sub1.setDisplayName("Travel Assistant");
            sub1.setDescription("Helps with travel planning");
            config.setSubAgents(List.of(sub1));

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertTrue(result.contains("Available Sub-Agents"));
            assertTrue(result.contains("Travel Assistant"));
            assertTrue(result.contains("Helps with travel planning"));
        }

        @Test
        @DisplayName("Should not include sub-agent section when subAgents is null")
        void noSubAgents() {
            AgentConfig config = createConfig();
            config.setSubAgents(null);

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertFalse(result.contains("Available Sub-Agents"));
        }
    }

    @Nested
    @DisplayName("assembleSystemPrompt - skills")
    class Skills {

        @Test
        @DisplayName("Should include skill list when agent has skills")
        void includeSkills() {
            AgentConfig config = createConfig();

            Skill skill = new Skill();
            skill.setId(UUID.randomUUID());
            skill.setName("code-review");
            skill.setDescription("Reviews code quality");

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of(skill));
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertTrue(result.contains("Available Skills"));
            assertTrue(result.contains("code-review"));
            assertTrue(result.contains("Reviews code quality"));
            assertTrue(result.contains("skill_read_file"));
        }

        @Test
        @DisplayName("Should not include skill section when agent has no skills")
        void noSkills() {
            AgentConfig config = createConfig();

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertFalse(result.contains("Available Skills"));
        }

        @Test
        @DisplayName("Should handle skill service exception gracefully")
        void skillServiceException() {
            AgentConfig config = createConfig();

            when(skillService.getSkillsForAgent(config.getAgentId()))
                    .thenThrow(new RuntimeException("DB error"));
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertNotNull(result);
            assertTrue(result.contains("You are a helpful assistant."));
        }
    }

    @Nested
    @DisplayName("assembleSystemPrompt - memory context")
    class MemoryContext {

        @Test
        @DisplayName("Should include memory context when available")
        void includeMemory() {
            AgentConfig config = createConfig();

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext("user1", "session1", config.getAgentId(), "hello"))
                    .thenReturn("## Memory\n- User prefers short answers");

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertTrue(result.contains("User prefers short answers"));
        }

        @Test
        @DisplayName("Should handle null memory context")
        void nullMemory() {
            AgentConfig config = createConfig();

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(null);

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle memory injector exception gracefully")
        void memoryInjectorException() {
            AgentConfig config = createConfig();

            when(skillService.getSkillsForAgent(config.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Redis error"));

            String result = assembler.assembleSystemPrompt(config, "user1", "session1", "hello");

            assertNotNull(result);
            assertTrue(result.contains("You are a helpful assistant."));
        }
    }

    @Nested
    @DisplayName("assembleSubAgentSystemPrompt")
    class SubAgentPrompt {

        @Test
        @DisplayName("Should use sub-agent's own system prompt")
        void subAgentOwnPrompt() {
            AgentConfig rootConfig = createConfig();
            ResolvedAgent subAgent = new ResolvedAgent(
                    "TravelBot", "Travel Assistant", "You are a travel expert.",
                    "gpt-4", List.of(), List.of(), false
            );

            when(skillService.getSkillsForAgent(rootConfig.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSubAgentSystemPrompt(subAgent, rootConfig, "user1", "session1", "hello");

            assertTrue(result.startsWith("You are a travel expert."));
        }

        @Test
        @DisplayName("Should include date/time in sub-agent prompt")
        void subAgentDateTime() {
            AgentConfig rootConfig = createConfig();
            ResolvedAgent subAgent = new ResolvedAgent(
                    "TravelBot", "Travel Assistant", "Travel prompt",
                    "gpt-4", List.of(), List.of(), false
            );

            when(skillService.getSkillsForAgent(rootConfig.getAgentId())).thenReturn(List.of());
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSubAgentSystemPrompt(subAgent, rootConfig, "user1", "session1", "hello");

            assertTrue(result.contains("Current date and time:"));
        }

        @Test
        @DisplayName("Should filter skills by sub-agent's skillIds")
        void subAgentFilteredSkills() {
            AgentConfig rootConfig = createConfig();

            UUID skillId1 = UUID.randomUUID();
            UUID skillId2 = UUID.randomUUID();

            Skill skill1 = new Skill();
            skill1.setId(skillId1);
            skill1.setName("search");
            skill1.setDescription("Search skill");

            Skill skill2 = new Skill();
            skill2.setId(skillId2);
            skill2.setName("translate");
            skill2.setDescription("Translate skill");

            ResolvedAgent subAgent = new ResolvedAgent(
                    "Bot", "Bot", "Bot prompt", "gpt-4",
                    List.of(), List.of(skillId1.toString()), false
            );

            when(skillService.getSkillsForAgent(rootConfig.getAgentId()))
                    .thenReturn(List.of(skill1, skill2));
            when(memoryInjector.buildMemoryContext(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("");

            String result = assembler.assembleSubAgentSystemPrompt(subAgent, rootConfig, "user1", "session1", "hello");

            assertTrue(result.contains("Available Skills"));
            assertTrue(result.contains("search"));
            assertFalse(result.contains("translate"));
        }
    }
}
