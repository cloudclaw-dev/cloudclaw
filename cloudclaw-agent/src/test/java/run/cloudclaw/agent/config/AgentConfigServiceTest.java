package run.cloudclaw.agent.config;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.model.Agent;
import run.cloudclaw.agent.repository.AgentRepository;
import run.cloudclaw.llm.repository.LlmModelRepository;
import run.cloudclaw.mcp.repository.AgentMcpServerRepository;
import run.cloudclaw.skill.repository.AgentSkillRepository;
import run.cloudclaw.sandbox.model.SandboxProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentConfigService")
class AgentConfigServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentMcpServerRepository agentMcpServerRepository;
    @Mock private AgentSkillRepository agentSkillRepository;
    @Mock private LlmModelRepository llmModelRepository;
    @Mock private SandboxProviderRepository sandboxProviderRepository;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    private AgentConfigService service;

    private static final UUID AGENT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new AgentConfigService(
                agentRepository, agentMcpServerRepository, agentSkillRepository,
                llmModelRepository, sandboxProviderRepository, cacheManager);
    }

    @Test
    @DisplayName("Agent 不存在应抛 BusinessException")
    void agentNotFound_throws() {
        when(cacheManager.getCache("agentConfig")).thenReturn(cache);
        when(cache.get(anyString(), eq(AgentConfig.class))).thenReturn(null);
        when(agentRepository.findById(AGENT_UUID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getAgentConfig(AGENT_UUID.toString()));
        assertTrue(ex.getMessage().contains("Agent not found"));
    }

    @Test
    @DisplayName("缓存命中应直接返回")
    void cacheHit() {
        when(cacheManager.getCache("agentConfig")).thenReturn(cache);
        AgentConfig cachedConfig = new AgentConfig();
        cachedConfig.setAgentId(AGENT_UUID.toString());
        when(cache.get(AGENT_UUID.toString(), AgentConfig.class)).thenReturn(cachedConfig);

        AgentConfig result = service.getAgentConfig(AGENT_UUID.toString());
        assertNotNull(result);
        assertEquals(AGENT_UUID.toString(), result.getAgentId());
        // 不应该查数据库
        verify(agentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("缓存未命中应查数据库并缓存结果")
    void cacheMiss_queriesDb() {
        when(cacheManager.getCache("agentConfig")).thenReturn(cache);
        when(cache.get(AGENT_UUID.toString(), AgentConfig.class)).thenReturn(null);

        Agent agent = new Agent();
        agent.setId(AGENT_UUID);
        agent.setName("Test Agent");
        agent.setEnabled(true);
        agent.setSystemPrompt("You are a test agent.");
        when(agentRepository.findById(AGENT_UUID)).thenReturn(Optional.of(agent));
        when(agentMcpServerRepository.findByAgentId(AGENT_UUID)).thenReturn(java.util.List.of());
        when(agentSkillRepository.findByAgentId(AGENT_UUID)).thenReturn(java.util.List.of());

        AgentConfig result = service.getAgentConfig(AGENT_UUID.toString());
        assertNotNull(result);
        assertEquals(AGENT_UUID.toString(), result.getAgentId());
        verify(cache).put(eq(AGENT_UUID.toString()), any(AgentConfig.class));
    }
}
