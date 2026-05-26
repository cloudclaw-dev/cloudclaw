package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.PipelineConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.agent.engine.ReactiveContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline executor: processes nodes sequentially.
 * The output of each step is passed as input to the next step.
 *
 * <p>Pipeline intermediate results are NOT saved to messages — only the final result.
 * Intermediate progress is emitted as SSE pipeline_step events.</p>
 */
@Component
@Slf4j
public class PipelineExecutor {

    private final WorkflowNodeResolver nodeResolver;
    private final WorkflowChatHelper chatHelper;
    private final Executor workflowExecutor;

    public PipelineExecutor(WorkflowNodeResolver nodeResolver,
                           WorkflowChatHelper chatHelper,
                           @Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.nodeResolver = nodeResolver;
        this.chatHelper = chatHelper;
        this.workflowExecutor = workflowExecutor;
    }

    public Flux<ChatChunk> execute(String userId, String sessionId,
                                    String userMessage, AgentConfig config, WorkflowDef workflow,
                                    List<Message> history) {
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);

        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        List<ResolvedNodeAgent> resolvedNodes = nodeResolver.resolveAll(nodes, config);

        PipelineConfig pipelineConfig = workflow.getPipelineConfig();
        String passthroughMode = pipelineConfig != null && pipelineConfig.getPassthroughMode() != null
                ? pipelineConfig.getPassthroughMode() : "append";

        Runnable pipelineThread = () -> {
            try {
                String previousStepResult = null;
                String fullResponse = "";

                for (int i = 0; i < resolvedNodes.size(); i++) {
                    ResolvedNodeAgent node = resolvedNodes.get(i);
                    int stepNum = i + 1;

                    log.info("Pipeline step {}/{}: node={}, sessionId={}", stepNum, resolvedNodes.size(), node.getName(), sessionId);

                    // Emit pipeline_step start event
                    ReactiveContextHelper.safeEmitNext(sink, ChatChunk.pipelineStep(stepNum, node.getDisplayName(),
                            "Processing via " + node.getDisplayName() + "..."));

                    // Build system prompt for this node using PromptAssembler
                    String systemPrompt = chatHelper.buildNodeSystemPrompt(node, config, userId, sessionId, userMessage);

                    // Prepare input based on passthrough mode
                    String effectiveInput;
                    if (i == 0) {
                        // First step always gets the original user message
                        effectiveInput = userMessage;
                    } else if ("replace".equals(passthroughMode)) {
                        // Replace mode: previous step result replaces user message
                        effectiveInput = previousStepResult;
                    } else {
                        // Append mode: original message + previous step result
                        effectiveInput = userMessage + "\n\n---\n\nPrevious step result:\n" + previousStepResult;
                    }

                    // Resolve tools for this node
                    List<McpSyncClient> mcpClients = new ArrayList<>();
                    AgentConfig nodeConfig = chatHelper.buildNodeConfig(config, node);
                    List<ToolCallback> toolCallbacks = new ArrayList<>(chatHelper.resolveToolCallbacks(nodeConfig, mcpClients));

                    int maxToolCalls = config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50;
                    int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
                    List<ToolCallback> truncatedCallbacks = toolCallbacks.stream()
                            .map(cb -> (ToolCallback) new run.cloudclaw.agent.engine.TruncatingToolCallback(cb, maxToolResultChars))
                            .toList();

                    // Call LLM (streaming, but block to ensure sequential execution)
                    WorkflowChatHelper.LogContext logCtx = new WorkflowChatHelper.LogContext(
                            sessionId, config.getAgentId().toString(), userId, node.getDisplayName());
                    Flux<String> stream = chatHelper.streamLlmWithTools(
                            node.getModelId(), systemPrompt, effectiveInput, truncatedCallbacks, maxToolCalls, history, logCtx);

                    StringBuilder stepResult = new StringBuilder();
                    stream.doOnNext(chunk -> {
                        stepResult.append(chunk);
                        // Stream text chunks to frontend as they arrive
                        ReactiveContextHelper.safeEmitNext(sink, ChatChunk.text(chunk));
                    }).doOnError(e -> {
                        log.error("Pipeline step {} error: {}", stepNum, e.getMessage());
                    }).blockLast(); // Block to ensure sequential execution

                    previousStepResult = stepResult.toString();
                    fullResponse = previousStepResult;

                    log.info("Pipeline step {} completed: node={}, resultLength={}", stepNum, node.getName(), fullResponse.length());

                    // Close MCP clients for this step
                    for (McpSyncClient client : mcpClients) {
                        try { client.close(); } catch (Exception ignored) {}
                    }
                }

                // Done
                ReactiveContextHelper.safeEmitNext(sink, chatHelper.buildDoneChunk(config, userMessage, fullResponse));
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Pipeline execution error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            }
        };

        workflowExecutor.execute(pipelineThread);

        return sink.asFlux();
    }
}
