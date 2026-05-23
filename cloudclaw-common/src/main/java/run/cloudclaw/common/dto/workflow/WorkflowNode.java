package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A single node in a workflow definition.
 * Represents either an inline-defined sub-agent or a reference to an existing agent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowNode {

    /** Unique node identifier within the workflow, e.g. "node_1" */
    private String id;

    /** Machine-readable name, e.g. "Drafter" */
    private String name;

    /** Human-readable display name, e.g. "起草助手" */
    @JsonAlias("display_name")
    private String displayName;

    /** If non-null, references an existing Agent by ID; if null, uses inline definition */
    @JsonAlias("ref_agent_id")
    private String refAgentId;

    /** Inline system prompt (used when refAgentId is null) */
    @JsonAlias("system_prompt")
    private String systemPrompt;

    /** Override model ID; null means inherit from parent agent */
    @JsonAlias("model_id")
    private String modelId;

    /** MCP server IDs for this node */
    @JsonAlias("mcp_server_ids")
    private List<String> mcpServerIds;

    /** Skill IDs for this node */
    @JsonAlias("skill_ids")
    private List<String> skillIds;

    /** Description of this node's capabilities (used by Router/Handoff for LLM routing) */
    private String description;
}
