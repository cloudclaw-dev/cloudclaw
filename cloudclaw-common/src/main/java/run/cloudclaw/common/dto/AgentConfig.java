package run.cloudclaw.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowMode;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    private String agentId;
    private String name;
    private String systemPrompt;
    private String modelId;
    private Double temperature;
    private Integer maxTokens;
    private Integer maxToolCalls;
    private Boolean enabled;
    private List<String> mcpServerIds;
    private List<String> skillIds;
    private Integer contextWindow;
    private Integer compressionThreshold;
    private Integer compressionKeepRounds;
    private Double contextUsageThreshold;
    private Integer maxToolResultChars;
    private Boolean enableMemoryTools;
    private Integer memoryProfileMaxTokens;
    private Integer memoryTaskMaxTokens;
    private Boolean sandboxEnabled;
    private String sandboxBackend;
    private String sandboxProviderId;
    private String sandboxMode;
    private Integer sandboxTimeout;

    // Welcome page display
    private String emoji;
    private Boolean featured;
    private String greetingMessage;
    private String suggestedPrompts;

    // Sub-agents (Agent Transfer v2)
    private List<SubAgentDef> subAgents;

    // Workflow v3
    /** Workflow mode string from DB */
    private String workflowMode;
    /** Parsed workflow definition */
    private WorkflowDef workflow;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubAgentDef {
        private String name;
        private String displayName;
        private String description;
        private String systemPrompt;
        private String modelId;
        private java.util.List<String> mcpServerIds;
        private java.util.List<String> skillIds;
    }
}
