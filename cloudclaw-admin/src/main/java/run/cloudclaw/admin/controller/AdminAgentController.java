package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.dto.CreateAgentRequest;
import run.cloudclaw.admin.dto.UpdateAgentRequest;
import run.cloudclaw.admin.repository.AdminAgentRepository;
import run.cloudclaw.admin.repository.AdminSessionRepository;
import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
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

    /**
     * Create a new agent with optional MCP server and skill bindings.
     *
     * @param request the agent creation request
     * @return the created agent
     */
    @PostMapping
    @Transactional
    public Result<Agent> createAgent(@Valid @RequestBody CreateAgentRequest request) {
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
        agent.setCreatedBy(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        Agent saved = agentRepository.save(agent);

        // Bind MCP servers
        if (request.getMcpServerIds() != null) {
            for (String serverId : request.getMcpServerIds()) {
                AgentMcpServer binding = new AgentMcpServer();
                binding.setAgentId(saved.getId());
                binding.setServerId(UUID.fromString(serverId));
                agentMcpServerRepository.save(binding);
            }
        }

        // Bind skills
        if (request.getSkillIds() != null) {
            for (String skillId : request.getSkillIds()) {
                AgentSkill binding = new AgentSkill();
                binding.setAgentId(saved.getId());
                binding.setSkillId(UUID.fromString(skillId));
                agentSkillRepository.save(binding);
            }
        }

        log.info("Agent created successfully with id: {}", saved.getId());
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "agent", saved.getId().toString());
        return Result.ok(saved);
    }

    /**
     * List all agents.
     *
     * @return list of all agents
     */
    @GetMapping
    public Result<List<Agent>> listAgents() {
        log.debug("Admin listing all agents");
        List<Agent> agents = agentRepository.findAll();
        agents.forEach(this::populateBindings);
        return Result.ok(agents);
    }

    /**
     * Get agent detail by ID.
     *
     * @param id the agent ID
     * @return the agent
     */
    @GetMapping("/{id}")
    public Result<Agent> getAgent(@PathVariable String id) {
        log.debug("Admin getting agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(404, "Agent not found: " + id));
        populateBindings(agent);

        return Result.ok(agent);
    }

    /**
     * Update an existing agent.
     *
     * @param id      the agent ID
     * @param request the update request
     * @return the updated agent
     */
    @PutMapping("/{id}")
    @Transactional
    public Result<Agent> updateAgent(@PathVariable String id,
                                     @Valid @RequestBody UpdateAgentRequest request) {
        log.info("Admin updating agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BusinessException(404, "Agent not found: " + id));

        if (request.getName() != null) {
            agent.setName(request.getName());
        }
        if (request.getDescription() != null) {
            agent.setDescription(request.getDescription());
        }
        if (request.getSystemPrompt() != null) {
            agent.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getModelId() != null) {
            agent.setModelId(request.getModelId());
        }
        if (request.getTemperature() != null) {
            agent.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            agent.setMaxTokens(request.getMaxTokens());
        }
        if (request.getMaxToolCalls() != null) {
            agent.setMaxToolCalls(request.getMaxToolCalls());
        }
        if (request.getCompressionThreshold() != null) {
            agent.setCompressionThreshold(request.getCompressionThreshold());
        }
        if (request.getCompressionKeepRounds() != null) {
            agent.setCompressionKeepRounds(request.getCompressionKeepRounds());
        }
        if (request.getContextUsageThreshold() != null) {
            agent.setContextUsageThreshold(request.getContextUsageThreshold());
        }
        if (request.getSandboxEnabled() != null) {
            agent.setSandboxEnabled(request.getSandboxEnabled());
        }
        if (request.getSandboxBackend() != null) {
            agent.setSandboxBackend(request.getSandboxBackend());
        }
        if (request.getSandboxMode() != null) {
            validateSandboxMode(
                request.getSandboxBackend() != null ? request.getSandboxBackend() : agent.getSandboxBackend(),
                request.getSandboxMode(),
                request.getSandboxProviderId() != null ? request.getSandboxProviderId() : agent.getSandboxProviderId()
            );
            agent.setSandboxMode(request.getSandboxMode());
        }
        if (request.getSandboxTimeout() != null) {
            agent.setSandboxTimeout(request.getSandboxTimeout());
        }
        if (request.getSandboxProviderId() != null) {
            agent.setSandboxProviderId(request.getSandboxProviderId());
        }
        if (request.getEnabled() != null) {
            agent.setEnabled(request.getEnabled());
        }

        Agent saved = agentRepository.save(agent);

        // Update MCP server bindings if provided
        if (request.getMcpServerIds() != null) {
            // Remove existing bindings
            List<AgentMcpServer> existingBindings = agentMcpServerRepository.findByAgentId(agentId);
            agentMcpServerRepository.deleteAll(existingBindings);

            // Add new bindings
            for (String serverId : request.getMcpServerIds()) {
                AgentMcpServer binding = new AgentMcpServer();
                binding.setAgentId(agentId);
                binding.setServerId(UUID.fromString(serverId));
                agentMcpServerRepository.save(binding);
            }
        }

        // Update skill bindings if provided
        if (request.getSkillIds() != null) {
            // Remove existing bindings
            List<AgentSkill> existingBindings = agentSkillRepository.findByAgentId(agentId);
            agentSkillRepository.deleteAll(existingBindings);

            // Add new bindings
            for (String skillId : request.getSkillIds()) {
                AgentSkill binding = new AgentSkill();
                binding.setAgentId(agentId);
                binding.setSkillId(UUID.fromString(skillId));
                agentSkillRepository.save(binding);
            }
        }

        log.info("Agent updated successfully: {}", id);
        agentConfigService.evictCache(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "agent", id);
        return Result.ok(saved);
    }

    /**
     * Delete an agent by ID.
     *
     * @param id the agent ID
     * @return empty result on success
     */
    private void populateBindings(Agent agent) {
        List<String> mcpIds = agentMcpServerRepository.findByAgentId(agent.getId())
                .stream().map(b -> b.getServerId().toString()).toList();
        agent.setMcpServerIds(mcpIds);

        List<String> skillIds = agentSkillRepository.findByAgentId(agent.getId())
                .stream().map(b -> b.getSkillId().toString()).toList();
        agent.setSkillIds(skillIds);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> deleteAgent(@PathVariable String id) {
        log.info("Admin deleting agent with id: {}", id);

        UUID agentId = UUID.fromString(id);
        if (!agentRepository.existsById(agentId)) {
            throw new BusinessException(404, "Agent not found: " + id);
        }

        // Remove associated MCP server bindings
        List<AgentMcpServer> mcpBindings = agentMcpServerRepository.findByAgentId(agentId);
        agentMcpServerRepository.deleteAll(mcpBindings);

        // Remove associated skill bindings
        List<AgentSkill> skillBindings = agentSkillRepository.findByAgentId(agentId);
        agentSkillRepository.deleteAll(skillBindings);

        // Remove associated sessions (and their messages via CASCADE)
        adminSessionRepository.deleteByAgentId(id);

        // Remove associated fragments (cleared via profile/session, no-op)

        // Remove the agent itself
        agentRepository.deleteById(agentId);
        agentConfigService.evictCache(id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "agent", id);
        log.info("Agent deleted successfully: {}", id);

        return Result.ok();
    }

    /**
     * Validate sandbox mode compatibility with backend.
     * LOCAL and DOCKER backends only support STATELESS mode.
     * Only E2B supports SESSION mode.
     */
    private void validateSandboxMode(String backend, String mode, String providerId) {
        if (mode == null || !"SESSION".equals(mode)) return;
        
        String effectiveBackend = backend;
        
        // If provider specified, resolve backend from provider
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
            throw new BusinessException(400, "SESSION mode is only supported with E2B backend. " + effectiveBackend + " only supports STATELESS mode.");
        }
    }
}