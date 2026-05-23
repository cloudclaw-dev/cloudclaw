package run.cloudclaw.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgentRequest {

    @Size(max = 100, message = "Agent name must be at most 100 characters")
    private String name;

    private String description;

    private String systemPrompt;

    @Size(max = 100, message = "Model ID must be at most 100 characters")
    private String modelId;

    private Double temperature;

    private Integer maxTokens;

    private Integer maxToolCalls;

    /** Max conversation rounds before triggering summary compression */
    private Integer compressionThreshold;

    /** Number of recent rounds to keep when compressing */
    private Integer compressionKeepRounds;

    /** Token usage threshold (0.0-1.0) for dynamic context compression */
    private Double contextUsageThreshold;

    // Sandbox
    private Boolean sandboxEnabled;
    private String sandboxBackend;
    private String sandboxProviderId;
    private String sandboxMode;
    private Integer sandboxTimeout;

    private List<String> mcpServerIds;

    private List<String> skillIds;

    private Boolean enabled;

    // Sub-agents JSON (Agent Transfer v2)
    private String subAgents;

    // Workflow v3
    /** Workflow mode: pipeline | parallel | router | supervisor | handoff */
    private String workflowMode;

    /** Workflow definition JSON */
    private String workflow;
}
