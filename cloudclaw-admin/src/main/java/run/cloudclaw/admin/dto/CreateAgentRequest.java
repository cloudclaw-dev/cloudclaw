package run.cloudclaw.admin.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateAgentRequest {

    @NotBlank(message = "Agent name is required")
    @Size(max = 100, message = "Agent name must be at most 100 characters")
    private String name;

    private String description;

    @NotBlank(message = "System prompt is required")
    private String systemPrompt;

    @Size(max = 100, message = "Model ID must be at most 100 characters")
    private String modelId;

    private Double temperature;

    private Integer maxTokens;

    private Integer maxToolCalls;

    private Integer compressionThreshold;
    private Integer compressionKeepRounds;
    private Double contextUsageThreshold;

    // Sandbox
    private Boolean sandboxEnabled;
    private String sandboxBackend;
    private String sandboxProviderId;
    private String sandboxMode;
    private Integer sandboxTimeout;

    private List<String> mcpServerIds;

    private List<String> skillIds;

    // Sub-agents JSON (Agent Transfer v2)
    private String subAgents;

    // Workflow v3
    /** Workflow mode: pipeline | parallel | router | supervisor | handoff */
    private String workflowMode;

    /** Workflow definition JSON */
    private String workflow;
}
