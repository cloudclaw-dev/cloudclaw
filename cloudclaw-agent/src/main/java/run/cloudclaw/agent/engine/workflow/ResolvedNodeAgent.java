package run.cloudclaw.agent.engine.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A resolved workflow node — contains all info needed to invoke a sub-agent.
 * Built from either an inline WorkflowNode definition or a referenced Agent.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ResolvedNodeAgent {

    /** Node ID from workflow definition */
    private String nodeId;

    /** Machine-readable name */
    private String name;

    /** Human-readable display name */
    private String displayName;

    /** System prompt to use */
    private String systemPrompt;

    /** Model ID (null = inherit from parent) */
    private String modelId;

    /** MCP server IDs */
    private List<String> mcpServerIds;

    /** Skill IDs */
    private List<String> skillIds;

    /** Description (for routing/handoff decisions) */
    private String description;
}
