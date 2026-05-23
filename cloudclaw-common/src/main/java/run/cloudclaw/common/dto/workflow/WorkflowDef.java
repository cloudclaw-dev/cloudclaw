package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Top-level workflow definition attached to an Agent.
 * Contains the mode, list of nodes, and mode-specific configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowDef {

    private WorkflowMode mode;
    private List<WorkflowNode> nodes;

    /** Mode-specific configs (only one is active based on mode) */
    @JsonProperty("pipeline_config")
    private PipelineConfig pipelineConfig;
    @JsonProperty("parallel_config")
    private ParallelConfig parallelConfig;
    @JsonProperty("router_config")
    private RouterConfig routerConfig;
    @JsonProperty("supervisor_config")
    private SupervisorConfig supervisorConfig;
    @JsonProperty("handoff_config")
    private HandoffConfig handoffConfig;
}
