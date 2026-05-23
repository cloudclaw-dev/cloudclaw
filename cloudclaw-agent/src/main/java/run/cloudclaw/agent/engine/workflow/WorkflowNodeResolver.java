package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.agent.config.AgentConfigService;
import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves a WorkflowNode into a ResolvedNodeAgent that can be used by executors.
 *
 * <p>Supports two resolution modes:</p>
 * <ol>
 *   <li><b>Inline definition</b>: uses the node's own prompt/model/tools</li>
 *   <li><b>Reference mode</b>: loads an existing Agent by refAgentId</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowNodeResolver {

    private final AgentConfigService agentConfigService;

    /**
     * Resolve a single WorkflowNode into a concrete ResolvedNodeAgent.
     *
     * @param node         the workflow node definition
     * @param parentConfig the parent agent's configuration (for inheriting defaults)
     * @return resolved agent info ready for invocation
     */
    public ResolvedNodeAgent resolve(WorkflowNode node, AgentConfig parentConfig) {
        if (node.getRefAgentId() != null && !node.getRefAgentId().isBlank()) {
            return resolveReferencedAgent(node, parentConfig);
        }
        return resolveInlineAgent(node, parentConfig);
    }

    /**
     * Resolve all nodes in a workflow definition.
     */
    public List<ResolvedNodeAgent> resolveAll(List<WorkflowNode> nodes, AgentConfig parentConfig) {
        return nodes.stream()
                .map(node -> resolve(node, parentConfig))
                .toList();
    }

    private ResolvedNodeAgent resolveInlineAgent(WorkflowNode node, AgentConfig parentConfig) {
        return ResolvedNodeAgent.builder()
                .nodeId(node.getId())
                .name(node.getName())
                .displayName(node.getDisplayName() != null ? node.getDisplayName() : node.getName())
                .systemPrompt(node.getSystemPrompt())
                .modelId(node.getModelId() != null ? node.getModelId() : parentConfig.getModelId())
                .mcpServerIds(node.getMcpServerIds() != null ? node.getMcpServerIds() : List.of())
                .skillIds(node.getSkillIds() != null ? node.getSkillIds() : List.of())
                .description(node.getDescription())
                .build();
    }

    private ResolvedNodeAgent resolveReferencedAgent(WorkflowNode node, AgentConfig parentConfig) {
        try {
            AgentConfig refConfig = agentConfigService.getAgentConfig(node.getRefAgentId());
            return ResolvedNodeAgent.builder()
                    .nodeId(node.getId())
                    .name(refConfig.getName())
                    .displayName(node.getDisplayName() != null ? node.getDisplayName() : refConfig.getName())
                    .systemPrompt(refConfig.getSystemPrompt())
                    .modelId(node.getModelId() != null ? node.getModelId() : refConfig.getModelId())
                    .mcpServerIds(refConfig.getMcpServerIds() != null ? refConfig.getMcpServerIds() : List.of())
                    .skillIds(refConfig.getSkillIds() != null ? refConfig.getSkillIds() : List.of())
                    .description(node.getDescription() != null ? node.getDescription() : refConfig.getName())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to resolve referenced agent {}, falling back to inline: {}", node.getRefAgentId(), e.getMessage());
            return resolveInlineAgent(node, parentConfig);
        }
    }
}
