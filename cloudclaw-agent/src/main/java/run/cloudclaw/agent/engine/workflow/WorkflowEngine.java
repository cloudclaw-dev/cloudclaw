package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Central workflow engine that dispatches to mode-specific executors.
 *
 * <p>Checks if an agent has a workflow configured and delegates execution
 * to the appropriate executor based on the workflow mode.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowEngine {

    private final PipelineExecutor pipelineExecutor;
    private final ParallelExecutor parallelExecutor;
    private final RouterExecutor routerExecutor;
    private final SupervisorExecutor supervisorExecutor;
    private final HandoffExecutor handoffExecutor;

    /**
     * Check if the given agent config has an active workflow.
     */
    public boolean hasWorkflow(AgentConfig config) {
        return config != null && config.getWorkflow() != null && config.getWorkflowMode() != null;
    }

    /**
     * Execute a workflow for the given agent. Dispatches to the appropriate executor.
     *
     * @param userId      the current user
     * @param sessionId   the current session
     * @param userMessage the user's message
     * @param config      the root agent configuration (must have workflow)
     * @return flux of chat chunks including workflow-specific events
     */
    public Flux<ChatChunk> execute(String userId, String sessionId,
                                    String userMessage, AgentConfig config,
                                    java.util.List<run.cloudclaw.common.model.Message> history) {
        WorkflowDef workflow = config.getWorkflow();
        WorkflowMode mode = workflow.getMode();

        log.info("Executing workflow: mode={}, nodes={}, sessionId={}, historyMessages={}",
                mode, workflow.getNodes() != null ? workflow.getNodes().size() : 0, sessionId,
                history != null ? history.size() : 0);

        return switch (mode) {
            case PIPELINE   -> pipelineExecutor.execute(userId, sessionId, userMessage, config, workflow, history);
            case PARALLEL   -> parallelExecutor.execute(userId, sessionId, userMessage, config, workflow, history);
            case ROUTER     -> routerExecutor.execute(userId, sessionId, userMessage, config, workflow, history);
            case SUPERVISOR -> supervisorExecutor.execute(userId, sessionId, userMessage, config, workflow, history);
            case HANDOFF    -> handoffExecutor.execute(userId, sessionId, userMessage, config, workflow, history);
        };
    }
}
