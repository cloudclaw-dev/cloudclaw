package run.cloudclaw.user.controller;

import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.Agent;
import run.cloudclaw.user.repository.AgentQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for agent information.
 *
 * <p>Provides read-only endpoints for users to discover and view
 * available agents. Only enabled agents are returned, and system
 * prompts are excluded from the response for security.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentQueryRepository agentQueryRepository;

    public AgentController(AgentQueryRepository agentQueryRepository) {
        this.agentQueryRepository = agentQueryRepository;
    }

    /**
     * List all enabled agents available to the authenticated user.
     *
     * <p>System prompts are cleared from the response to prevent
     * users from viewing internal agent configuration.</p>
     *
     * @param userId the authenticated user ID, injected from JWT
     * @return list of enabled agents without system prompts
     */
    @GetMapping
    public Result<List<Agent>> listAgents(@AuthUser String userId) {
        log.debug("User [{}] listing available agents", userId);
        List<Agent> agents = agentQueryRepository.findByEnabledTrue();
        // Clear system prompts before returning to the user
        agents.forEach(agent -> agent.setSystemPrompt(null));
        return Result.ok(agents);
    }

    /**
     * Get detailed information about a specific agent.
     *
     * <p>The system prompt field is cleared before returning to prevent
     * users from inspecting internal agent instructions.</p>
     *
     * @param userId the authenticated user ID, injected from JWT
     * @param id     the agent ID
     * @return the agent details without system prompt
     */
    @GetMapping("/{id}")
    public Result<Agent> getAgent(@AuthUser String userId, @PathVariable String id) {
        log.debug("User [{}] getting agent [{}]", userId, id);
        Agent agent = agentQueryRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND, id));
        if (!agent.getEnabled()) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND, id);
        }
        // Clear system prompt before returning to the user
        agent.setSystemPrompt(null);
        return Result.ok(agent);
    }
}
