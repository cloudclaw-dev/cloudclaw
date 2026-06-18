package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.dto.CreateAgentRequest;
import run.cloudclaw.admin.dto.UpdateAgentRequest;
import run.cloudclaw.admin.repository.AdminAgentRepository;
import run.cloudclaw.admin.repository.AdminSessionRepository;
import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.Agent;
import run.cloudclaw.common.model.AgentMcpServer;
import run.cloudclaw.common.model.AgentSkill;
import run.cloudclaw.mcp.repository.AgentMcpServerRepository;
import run.cloudclaw.skill.repository.AgentSkillRepository;
import run.cloudclaw.sandbox.model.SandboxProviderRepository;
import run.cloudclaw.sandbox.model.SandboxProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AdminAgentRepository agentRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final AgentMcpServerRepository agentMcpServerRepository;
    private final AgentSkillRepository agentSkillRepository;
    private final SandboxProviderRepository sandboxProviderRepository;
    private final AgentConfigService agentConfigService;
    private final ConfigChangeNotifier configChangeNotifier;

    @PostMapping
    @Transactional
    public Result<Agent> createAgent(@Valid @RequestBody CreateAgentRequest request,
                                     // Fix: 从 SecurityContext 获取当前登录用户 ID 作为 createdBy，而非硬编码
                                     @AuthUser String userId) {
        log.info("Admin creating agent with name: {}", request.getName());

        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription());
        agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelId(request.getModelId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxToolCalls(request.getMaxToolCalls());
        agent.setCompressionThreshold(request.getCompressionThreshold());
        agent.setCompressionKeepRounds(request.getCompressionKeepRounds());
        agent.setContextUsageThreshold(request.getContextUsageThreshold());
        agent.setSandboxEnabled(request.getSandboxEnabled());
        agent.setSandboxBackend(request.getSandboxBackend());
        agent.setSandboxMode(request.getSandboxMode());
        agent.setSandboxTimeout(request.getSandboxTimeout());
        agent.setSandboxProviderId(request.getSandboxProviderId());
        validateSandboxMode(request.getSandboxBackend(), request.getSandboxMode(), request.getSandboxProviderId());
        agent.setEnabled(true);
        agent.setCreatedBy(UUID.fromString(userId != null ? userId : "00000000-0000-0000-0000-000000000001"));

        // Sub-agents JSON (Agent Transfer v2)
        if (request.getSubAgents() != null) {
            agent.setSubAgents(request.getSubAgents());
        }

        // Workflow v3
        if (request.getWorkflowMode() != null) {
            agent.setWorkflowMode(request.getWorkflowMode());
        }
        if (request.getWorkflow() != null) {
            agent.setWorkflow(request.getWorkflow());
        }

        Agent saved = agentRepository.save(agent);

        // Bind MCP servers
        if (request.getMcpServerIds() != null) {
            List<AgentMcpServer> mcpBindings = new ArrayList<>();
            for (String serverId : request.getMcpServerIds()) {
                AgentMcpServer binding = new AgentMcpServer();
                binding.setAgentId(saved.getId());
                binding.setServerId(UUID.fromString(serverId));
                mcpBindings.add(binding);
            }
            agentMcpServerRepository.saveAll(mcpBindings);
        }

        // Bind skills
        if (request.getSkillIds() != null) {
            List<AgentSkill> skillBindings = new ArrayList<>();
            for (String skillId : request.getSkillIds()) {
                AgentSkill binding = new AgentSkill();
                binding.setAgentId(saved.getId());
                binding.setSkillId(UUID.fromString(skillId));
                skillBindings.add(binding);
            }
            agentSkillRepository.saveAll(skillBindings);
        }

        log.info("Agent created successfully with id: {}", saved.getId());
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "agent", saved.getId().toString());
        return Result.ok(filterResponse(saved));
    }

    @GetMapping
    public Result<List<Agent>> listAgents() {
        log.debug("Admin listing all agents");
        List<Agent> agents = agentRepository.findAll();
        agents.forEach(this::populateBindings);
        agents.forEach(this::filterResponse);

        // Populate session counts
        List<Object[]> counts = adminSessionRepository.countByAgentId();
        java.util.Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : counts) {
            countMap.put(String.valueOf(row[0]), (Long) row[1]);
        }
        for (Agent agent : agents) {
            agent.setSessionCount(countMap.getOrDefault(agent.getId().toString(), 0L));
        }

        return Result.ok(agents);
    }

    @GetMapping("/{id}")
    public Result<Agent> getAgent(@PathVariable String id) {
        log.debug("Admin getting agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND, id));
        populateBindings(agent);
        return Result.ok(agent);
    }

    @PutMapping("/{id}")
    @Transactional
    public Result<Agent> updateAgent(@PathVariable String id,
                                     @Valid @RequestBody UpdateAgentRequest request) {
        log.info("Admin updating agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND, id));

        if (request.getName() != null) agent.setName(request.getName());
        if (request.getDescription() != null) agent.setDescription(request.getDescription());
        if (request.getSystemPrompt() != null) agent.setSystemPrompt(request.getSystemPrompt());
        if (request.getModelId() != null) agent.setModelId(request.getModelId());
        if (request.getTemperature() != null) agent.setTemperature(request.getTemperature());
        if (request.getMaxTokens() != null) agent.setMaxTokens(request.getMaxTokens());
        if (request.getMaxToolCalls() != null) agent.setMaxToolCalls(request.getMaxToolCalls());
        if (request.getCompressionThreshold() != null) agent.setCompressionThreshold(request.getCompressionThreshold());
        if (request.getCompressionKeepRounds() != null) agent.setCompressionKeepRounds(request.getCompressionKeepRounds());
        if (request.getContextUsageThreshold() != null) agent.setContextUsageThreshold(request.getContextUsageThreshold());
        if (request.getSandboxEnabled() != null) agent.setSandboxEnabled(request.getSandboxEnabled());
        if (request.getSandboxBackend() != null) agent.setSandboxBackend(request.getSandboxBackend());
        if (request.getSandboxMode() != null) {
            validateSandboxMode(
                request.getSandboxBackend() != null ? request.getSandboxBackend() : agent.getSandboxBackend(),
                request.getSandboxMode(),
                request.getSandboxProviderId() != null ? request.getSandboxProviderId() : agent.getSandboxProviderId()
            );
            agent.setSandboxMode(request.getSandboxMode());
        }
        if (request.getSandboxTimeout() != null) agent.setSandboxTimeout(request.getSandboxTimeout());
        if (request.getSandboxProviderId() != null) agent.setSandboxProviderId(request.getSandboxProviderId().isBlank() ? null : request.getSandboxProviderId());
        if (request.getEnabled() != null) agent.setEnabled(request.getEnabled());

        // Sub-agents JSON (Agent Transfer v2)
        if (request.getSubAgents() != null) {
            agent.setSubAgents(request.getSubAgents());
        }

        // Workflow v3
        if (request.getWorkflowMode() != null) {
            agent.setWorkflowMode(request.getWorkflowMode());
        }
        // Allow clearing workflow by sending empty string
        if (request.getWorkflow() != null) {
            agent.setWorkflow(request.getWorkflow().isBlank() ? null : request.getWorkflow());
        }

        // Welcome page fields
        if (request.getEmoji() != null) agent.setEmoji(request.getEmoji());
        if (request.getFeatured() != null) agent.setFeatured(request.getFeatured());
        if (request.getGreetingMessage() != null) agent.setGreetingMessage(request.getGreetingMessage());
        if (request.getSuggestedPrompts() != null) agent.setSuggestedPrompts(request.getSuggestedPrompts());

        Agent saved = agentRepository.save(agent);

        // Update MCP server bindings if provided
        if (request.getMcpServerIds() != null) {
            List<AgentMcpServer> existingBindings = agentMcpServerRepository.findByAgentId(agentId);
            agentMcpServerRepository.deleteAll(existingBindings);

            List<AgentMcpServer> mcpBindings = new ArrayList<>();
            for (String serverId : request.getMcpServerIds()) {
                AgentMcpServer binding = new AgentMcpServer();
                binding.setAgentId(agentId);
                binding.setServerId(UUID.fromString(serverId));
                mcpBindings.add(binding);
            }
            agentMcpServerRepository.saveAll(mcpBindings);
        }

        // Update skill bindings if provided
        if (request.getSkillIds() != null) {
            List<AgentSkill> existingBindings = agentSkillRepository.findByAgentId(agentId);
            agentSkillRepository.deleteAll(existingBindings);

            List<AgentSkill> skillBindings = new ArrayList<>();
            for (String skillId : request.getSkillIds()) {
                AgentSkill binding = new AgentSkill();
                binding.setAgentId(agentId);
                binding.setSkillId(UUID.fromString(skillId));
                skillBindings.add(binding);
            }
            agentSkillRepository.saveAll(skillBindings);
        }

        log.info("Agent updated successfully: {}", id);
        agentConfigService.evictCache(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "agent", id);
        return Result.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> deleteAgent(@PathVariable String id) {
        log.info("Admin deleting agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        if (!agentRepository.existsById(agentId)) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, id);
        }

        List<AgentMcpServer> mcpBindings = agentMcpServerRepository.findByAgentId(agentId);
        agentMcpServerRepository.deleteAll(mcpBindings);

        List<AgentSkill> skillBindings = agentSkillRepository.findByAgentId(agentId);
        agentSkillRepository.deleteAll(skillBindings);

        adminSessionRepository.deleteByAgentId(id);

        agentRepository.deleteById(agentId);
        agentConfigService.evictCache(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "agent", id);
        log.info("Agent deleted successfully: {}", id);

        return Result.ok();
    }

    private void populateBindings(Agent agent) {
        List<String> mcpIds = agentMcpServerRepository.findByAgentId(agent.getId())
                .stream().map(b -> b.getServerId().toString()).toList();
        agent.setMcpServerIds(mcpIds);

        List<String> skillIds = agentSkillRepository.findByAgentId(agent.getId())
                .stream().map(b -> b.getSkillId().toString()).toList();
        agent.setSkillIds(skillIds);
    }

    /**
     * Fix H3: Filter out internal raw JSON fields from Agent responses.
     * The sub_agents and workflow are stored as raw JSON strings internally
     * but should not be exposed in API responses (they are parsed into structured config).
     */
    private Agent filterResponse(Agent agent) {
        agent.setSubAgents(null);
        agent.setWorkflow(null);
        return agent;
    }

    private void validateSandboxMode(String backend, String mode, String providerId) {
        if (mode == null || !"SESSION".equals(mode)) return;

        String effectiveBackend = backend;

        if (providerId != null && (backend == null || backend.isBlank())) {
            try {
                SandboxProvider provider = sandboxProviderRepository.findById(java.util.UUID.fromString(providerId)).orElse(null);
                if (provider != null) {
                    effectiveBackend = provider.getType();
                }
            } catch (Exception ignored) {}
        }

        if (effectiveBackend == null) effectiveBackend = "LOCAL";

        if ("LOCAL".equals(effectiveBackend) || "DOCKER".equals(effectiveBackend)) {
            throw new BusinessException(ErrorCode.AGENT_SESSION_MODE_UNSUPPORTED, "SESSION mode is only supported with E2B backend. " + effectiveBackend + " only supports STATELESS mode.");
        }
    }
}
