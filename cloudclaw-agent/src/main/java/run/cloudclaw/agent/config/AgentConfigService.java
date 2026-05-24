package run.cloudclaw.agent.config;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;

import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.repository.LlmModelRepository;
import run.cloudclaw.mcp.repository.AgentMcpServerRepository;
import run.cloudclaw.agent.repository.AgentRepository;
import run.cloudclaw.skill.repository.AgentSkillRepository;
import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.model.Agent;
import run.cloudclaw.common.model.AgentMcpServer;
import run.cloudclaw.common.model.AgentSkill;
import run.cloudclaw.sandbox.model.SandboxProvider;
import run.cloudclaw.sandbox.model.SandboxProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for loading and resolving Agent configuration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentConfigService {

    // Fix: 提取 ObjectMapper 为静态常量，避免每次调用 resolveConfig 都创建新实例
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final String CACHE_NAME = "agentConfig";

    private final AgentRepository agentRepository;
    private final AgentMcpServerRepository agentMcpServerRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final LlmModelRepository llmModelRepository;
    private final SandboxProviderRepository sandboxProviderRepository;
    private final CacheManager cacheManager;

    public AgentConfig getAgentConfig(String agentId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            AgentConfig cached = cache.get(agentId, AgentConfig.class);
            if (cached != null) {
                log.debug("Agent config cache hit for agentId={}", agentId);
                return cached;
            }
        }

        UUID uuid = UUID.fromString(agentId);
        Agent agent = agentRepository.findById(uuid)
                .orElseThrow(() -> {
                    log.warn("Agent not found: {}", agentId);
                    return new BusinessException(ErrorCode.AGENT_NOT_FOUND, agentId);
                });

        AgentConfig config = resolveConfig(agent);

        if (cache != null) {
            cache.put(agentId, config);
            log.debug("Agent config loaded and cached for agentId={}", agentId);
        }

        return config;
    }

    public List<AgentConfig> listAvailableAgents() {
        List<Agent> enabledAgents = agentRepository.findByEnabledTrue();
        return enabledAgents.stream()
                .map(this::resolveConfig)
                .toList();
    }

    private AgentConfig resolveConfig(Agent agent) {
        UUID agentUuid = agent.getId();

        List<String> mcpServerIds = agentMcpServerRepository.findByAgentId(agentUuid)
                .stream()
                .map(AgentMcpServer::getServerId)
                .map(UUID::toString)
                .toList();

        List<String> skillIds = agentSkillRepository.findByAgentId(agentUuid)
                .stream()
                .map(AgentSkill::getSkillId)
                .map(UUID::toString)
                .toList();

        AgentConfig config = new AgentConfig();
        config.setAgentId(agent.getId().toString());
        config.setName(agent.getName());
        config.setSystemPrompt(agent.getSystemPrompt());
        config.setModelId(agent.getModelId());
        config.setTemperature(agent.getTemperature());
        config.setMaxTokens(agent.getMaxTokens());
        config.setMaxToolCalls(agent.getMaxToolCalls());
        config.setEnabled(agent.getEnabled());
        config.setMcpServerIds(mcpServerIds);
        config.setSkillIds(skillIds);

        // Load context window from LLM model config
        try {
            LlmModel model = llmModelRepository.findById(agent.getModelId()).orElse(null);
            if (model != null && model.getContextWindow() != null) {
                config.setContextWindow(model.getContextWindow());
            } else {
                config.setContextWindow(128000);
            }
        } catch (Exception e) {
            config.setContextWindow(128000);
        }

        config.setCompressionThreshold(agent.getCompressionThreshold());
        config.setCompressionKeepRounds(agent.getCompressionKeepRounds());
        config.setContextUsageThreshold(agent.getContextUsageThreshold());
        config.setMaxToolResultChars(agent.getMaxToolResultChars() != null ? agent.getMaxToolResultChars() : 3000);

        config.setEnableMemoryTools(agent.getEnableMemoryTools() != null ? agent.getEnableMemoryTools() : true);
        config.setMemoryProfileMaxTokens(agent.getMemoryProfileMaxTokens());
        config.setMemoryTaskMaxTokens(agent.getMemoryTaskMaxTokens());

        config.setSandboxEnabled(agent.getSandboxEnabled() != null ? agent.getSandboxEnabled() : false);
        config.setSandboxMode(agent.getSandboxMode() != null ? agent.getSandboxMode() : "STATELESS");
        config.setSandboxTimeout(agent.getSandboxTimeout() != null ? agent.getSandboxTimeout() : 30);
        config.setSandboxProviderId(agent.getSandboxProviderId());

        // Parse sub_agents JSON (Agent Transfer v2)
        if (agent.getSubAgents() != null && !agent.getSubAgents().isBlank()) {
            try {
                java.util.List<AgentConfig.SubAgentDef> subAgents = MAPPER.readValue(
                        agent.getSubAgents(),
                        MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, AgentConfig.SubAgentDef.class));
                config.setSubAgents(subAgents);
                log.debug("Parsed {} sub-agents for agent {}", subAgents.size(), agent.getId());
            } catch (Exception e) {
                log.warn("Failed to parse sub_agents for agent {}: {}", agent.getId(), e.getMessage());
            }
        }

        // Parse workflow JSON (Workflow v3)
        config.setWorkflowMode(agent.getWorkflowMode());
        log.info("Loading agent {}: workflowMode={}, workflow={}", agent.getId(),
                agent.getWorkflowMode(),
                agent.getWorkflow() != null ? agent.getWorkflow().length() + " chars" : "null");
        if (agent.getWorkflow() != null && !agent.getWorkflow().isBlank()) {
            try {
                run.cloudclaw.common.dto.workflow.WorkflowDef workflow = MAPPER.readValue(
                        agent.getWorkflow(), run.cloudclaw.common.dto.workflow.WorkflowDef.class);
                config.setWorkflow(workflow);
                log.debug("Parsed workflow for agent {}: mode={}, nodes={}",
                        agent.getId(), workflow.getMode(),
                        workflow.getNodes() != null ? workflow.getNodes().size() : 0);
            } catch (Exception e) {
                log.warn("Failed to parse workflow for agent {}: {}", agent.getId(), e.getMessage());
            }
        }

        // Resolve sandbox backend from provider if set
        if (agent.getSandboxProviderId() != null) {
            try {
                SandboxProvider provider = sandboxProviderRepository.findById(UUID.fromString(agent.getSandboxProviderId())).orElse(null);
                if (provider != null && Boolean.TRUE.equals(provider.getEnabled())) {
                    config.setSandboxBackend(provider.getType());
                    if (provider.getDefaultTimeout() != null) config.setSandboxTimeout(provider.getDefaultTimeout());
                } else {
                    config.setSandboxBackend("LOCAL");
                }
            } catch (Exception e) {
                log.warn("Failed to resolve sandbox provider {}: {}", agent.getSandboxProviderId(), e.getMessage());
                config.setSandboxBackend("LOCAL");
            }
        } else {
            config.setSandboxBackend(agent.getSandboxBackend() != null ? agent.getSandboxBackend() : "LOCAL");
        }

        return config;
    }

    public void evictCache(String agentId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(agentId);
            log.info("Evicted agent config cache for agentId={}", agentId);
        }
    }
}
