package run.cloudclaw.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime state for a workflow execution, stored in session.workflow_state.
 *
 * <p>The structure varies by mode:</p>
 * <ul>
 *   <li>Pipeline: currentStep, stepResults</li>
 *   <li>Parallel: pendingNodes, completed, errors</li>
 *   <li>Supervisor: iteration, taskPlan, currentTask, taskResults</li>
 *   <li>Router/Handoff: activeNodeId</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowState {

    /** The workflow mode this state belongs to */
    private WorkflowMode mode;

    // ========== Pipeline state ==========
    /** Current step index (0-based) in the pipeline */
    private Integer currentStep;
    /** Results from each completed pipeline step, indexed by step number */
    @Builder.Default
    private List<String> stepResults = new java.util.ArrayList<>();

    // ========== Parallel state ==========
    /** Node IDs still pending execution */
    @Builder.Default
    private List<String> pendingNodes = new java.util.ArrayList<>();
    /** Completed node results: nodeId -> result text */
    @Builder.Default
    private Map<String, String> completed = new HashMap<>();
    /** Errored node results: nodeId -> error message */
    @Builder.Default
    private Map<String, String> errors = new HashMap<>();

    // ========== Supervisor state ==========
    /** Current iteration count */
    private Integer iteration;
    /** The supervisor's task plan */
    private List<String> taskPlan;
    /** Current task index in the plan */
    private Integer currentTask;
    /** Task results: nodeId -> result text */
    @Builder.Default
    private Map<String, String> taskResults = new HashMap<>();

    // ========== Router/Handoff state ==========
    /** Currently active node ID */
    private String activeNodeId;

    // ========== Factory methods ==========

    public static WorkflowState forPipeline() {
        return WorkflowState.builder()
                .mode(WorkflowMode.PIPELINE)
                .currentStep(0)
                .stepResults(new java.util.ArrayList<>())
                .build();
    }

    public static WorkflowState forParallel(List<String> nodeIds) {
        return WorkflowState.builder()
                .mode(WorkflowMode.PARALLEL)
                .pendingNodes(new java.util.ArrayList<>(nodeIds))
                .completed(new HashMap<>())
                .errors(new HashMap<>())
                .build();
    }

    public static WorkflowState forSupervisor() {
        return WorkflowState.builder()
                .mode(WorkflowMode.SUPERVISOR)
                .iteration(0)
                .taskResults(new HashMap<>())
                .build();
    }

    public static WorkflowState forRouter(String activeNodeId) {
        return WorkflowState.builder()
                .mode(WorkflowMode.ROUTER)
                .activeNodeId(activeNodeId)
                .build();
    }

    public static WorkflowState forHandoff(String activeNodeId) {
        return WorkflowState.builder()
                .mode(WorkflowMode.HANDOFF)
                .activeNodeId(activeNodeId)
                .build();
    }
}
