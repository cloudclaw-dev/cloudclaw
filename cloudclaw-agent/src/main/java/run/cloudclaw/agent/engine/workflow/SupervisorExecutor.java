package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.SupervisorConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Supervisor executor: a supervisor agent plans, delegates to sub-agents,
 * reviews results, and iterates until satisfied or max iterations reached.
 *
 * <p>The supervisor is given {@code delegate_to_xxx} tools. When it calls one,
 * the executor runs the target sub-agent and feeds the result back. The loop
 * continues until the supervisor provides a final text response (no tool call)
 * or the maximum iteration count is reached.</p>
 *
 * <p>Design note: the delegate tool captures delegation intent (nodeId + task) but does NOT
 * execute the sub-agent inline. Instead, the main loop detects the delegation and executes
 * the sub-agent, allowing SSE events to be emitted in the correct order.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SupervisorExecutor {

    private final WorkflowNodeResolver nodeResolver;
    private final WorkflowChatHelper chatHelper;

    public Flux<ChatChunk> execute(String userId, String sessionId,
                                    String userMessage, AgentConfig config, WorkflowDef workflow) {
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);

        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        List<ResolvedNodeAgent> resolvedNodes = nodeResolver.resolveAll(nodes, config);

        SupervisorConfig supervisorConfig = workflow.getSupervisorConfig();
        int maxIterations = supervisorConfig != null ? supervisorConfig.getMaxIterations() : 5;

        Thread supervisorThread = new Thread(() -> {
            try {
                // Emit supervisor_plan event at the start with the list of available agents
                List<String> agentNames = resolvedNodes.stream()
                        .map(ResolvedNodeAgent::getDisplayName)
                        .collect(Collectors.toList());
                sink.tryEmitNext(ChatChunk.supervisorPlan(agentNames));

                // Build supervisor system prompt
                String supervisorPrompt = buildSupervisorPrompt(config, resolvedNodes, supervisorConfig);

                // Iteration loop
                StringBuilder conversationContext = new StringBuilder(userMessage);
                String finalResult = null;

                for (int iteration = 1; iteration <= maxIterations; iteration++) {
                    log.info("Supervisor iteration {}/{}, sessionId={}", iteration, maxIterations, sessionId);

                    // Build delegate tools that capture delegation intent
                    AtomicReference<String> delegatedNodeId = new AtomicReference<>(null);
                    AtomicReference<String> delegatedTask = new AtomicReference<>(null);
                    List<ToolCallback> delegateTools = buildDelegateTools(resolvedNodes, delegatedNodeId, delegatedTask);

                    // Call supervisor with tools
                    String supervisorInput = iteration == 1 ? userMessage : conversationContext.toString();

                    int maxToolCalls = config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50;
                    WorkflowChatHelper.LogContext supervisorLogCtx = new WorkflowChatHelper.LogContext(
                            sessionId, config.getAgentId().toString(), userId, "supervisor");
                    String supervisorResponse;
                    try {
                        supervisorResponse = chatHelper.callLlmWithTools(config.getModelId(), supervisorPrompt,
                                supervisorInput, delegateTools, maxToolCalls, supervisorLogCtx);
                    } catch (Exception e) {
                        log.warn("Supervisor tool call failed, falling back to direct: {}", e.getMessage());
                        supervisorResponse = chatHelper.callLlm(config.getModelId(), supervisorPrompt, supervisorInput, supervisorLogCtx);
                    }

                    // Check if delegation happened
                    String targetNodeId = delegatedNodeId.get();
                    if (targetNodeId != null) {
                        // Supervisor delegated to a sub-agent
                        ResolvedNodeAgent targetNode = resolvedNodes.stream()
                                .filter(n -> n.getNodeId().equals(targetNodeId))
                                .findFirst().orElseThrow();

                        // Emit delegate SSE event BEFORE executing
                        sink.tryEmitNext(ChatChunk.supervisorDelegate(targetNode.getDisplayName(), delegatedTask.get()));

                        // Execute the delegated sub-agent
                        String nodePrompt = chatHelper.buildNodeSystemPrompt(targetNode, config, userId, sessionId, userMessage);
                        String task = delegatedTask.get() != null ? delegatedTask.get() : supervisorInput;

                        String result;
                        try {
                            WorkflowChatHelper.LogContext nodeLogCtx = new WorkflowChatHelper.LogContext(
                                    sessionId, config.getAgentId().toString(), userId, targetNode.getDisplayName());
                            result = chatHelper.callLlm(targetNode.getModelId(), nodePrompt, task, nodeLogCtx);
                        } catch (Exception e) {
                            result = "[Error executing " + targetNode.getDisplayName() + ": " + e.getMessage() + "]";
                        }

                        // Emit result SSE event AFTER executing
                        sink.tryEmitNext(ChatChunk.supervisorResult(targetNode.getDisplayName(), result));

                        // Feed result back to supervisor for next iteration
                        conversationContext = new StringBuilder();
                        conversationContext.append("Original task: ").append(userMessage).append("\n\n");
                        conversationContext.append("Delegated to ").append(targetNode.getDisplayName())
                                .append(" with task: ").append(delegatedTask.get()).append("\n\n");
                        conversationContext.append("Result from ").append(targetNode.getDisplayName()).append(":\n")
                                .append(result).append("\n\n");
                        conversationContext.append("Please review the result. If satisfied, provide a final summary. ");
                        conversationContext.append("If not satisfied, delegate to another agent with specific instructions. ");

                        finalResult = result;
                    } else {
                        // Supervisor responded directly (satisfied with results or initial response)
                        sink.tryEmitNext(ChatChunk.supervisorPlan(
                                List.of("Supervisor provided final response (iteration " + iteration + ")")));
                        finalResult = supervisorResponse;
                        break;
                    }
                }

                if (finalResult == null) {
                    finalResult = "Supervisor reached maximum iterations without a final result.";
                }

                // Emit final result as text
                sink.tryEmitNext(ChatChunk.text(finalResult));

                // Done
                sink.tryEmitNext(chatHelper.buildDoneChunk(config, userMessage, finalResult));
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Supervisor execution error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            }
        });

        supervisorThread.setName("supervisor-" + sessionId);
        supervisorThread.setDaemon(true);
        supervisorThread.start();

        return sink.asFlux();
    }

    private String buildSupervisorPrompt(AgentConfig config, List<ResolvedNodeAgent> nodes,
                                          SupervisorConfig supervisorConfig) {
        StringBuilder sb = new StringBuilder();
        if (config.getSystemPrompt() != null) {
            sb.append(config.getSystemPrompt()).append("\n\n");
        }
        sb.append("## Supervisor Mode\n\n");
        sb.append("You are a supervisor agent. Analyze the user's task, delegate sub-tasks to specialized agents, ");
        sb.append("review their results, and iterate until the task is complete.\n\n");

        if (supervisorConfig != null && supervisorConfig.getPlannerPrompt() != null) {
            sb.append(supervisorConfig.getPlannerPrompt()).append("\n\n");
        }

        sb.append("### Available Agents (use delegate_to_xxx tools):\n\n");
        for (ResolvedNodeAgent node : nodes) {
            sb.append("- **").append(node.getName()).append("**");
            if (node.getDescription() != null && !node.getDescription().isBlank()) {
                sb.append(": ").append(node.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n");

        if (supervisorConfig != null && supervisorConfig.getReviewerPrompt() != null) {
            sb.append("### Review Guidelines:\n").append(supervisorConfig.getReviewerPrompt()).append("\n\n");
        }

        sb.append("Instructions:\n");
        sb.append("1. Analyze the task and call the appropriate delegate_to_xxx tool to assign work.\n");
        sb.append("2. Review the result returned by the sub-agent.\n");
        sb.append("3. If satisfied, provide a final summary response (without calling any tool).\n");
        sb.append("4. If not satisfied, delegate again with revised instructions.\n");

        return sb.toString();
    }

    /**
     * Build delegate tools that capture delegation intent (node ID + task) without executing.
     * The actual sub-agent execution happens in the main loop so SSE events are properly ordered.
     */
    private List<ToolCallback> buildDelegateTools(List<ResolvedNodeAgent> nodes,
                                                    AtomicReference<String> delegatedNode,
                                                    AtomicReference<String> delegatedTask) {
        List<ToolCallback> tools = new ArrayList<>();
        for (ResolvedNodeAgent node : nodes) {
            String toolName = "delegate_to_" + toSnakeCase(node.getName());
            String baseDesc = "Delegate a task to " + node.getDisplayName();
            String nodeDesc = node.getDescription();
            String toolDescription = nodeDesc != null ? baseDesc + ". " + nodeDesc : baseDesc;
            final String nodeId = node.getNodeId();

            tools.add(new ToolCallback() {
                private final ToolDefinition toolDef = ToolDefinition.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema("{\"type\":\"object\",\"properties\":{" +
                                "\"task\":{\"type\":\"string\",\"description\":\"The specific task to assign\"}," +
                                "\"context\":{\"type\":\"string\",\"description\":\"Additional context (optional)\"}" +
                                "},\"required\":[\"task\"]}")
                        .build();

                @Override
                public ToolDefinition getToolDefinition() { return toolDef; }

                @Override
                public String call(String toolInput) {
                    String task = "";
                    try {
                        if (toolInput != null && !toolInput.isBlank()) {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(toolInput);
                            if (jsonNode.has("task")) task = jsonNode.get("task").asText();
                        }
                    } catch (Exception e) {
                        task = toolInput != null ? toolInput : "";
                    }
                    delegatedNode.set(nodeId);
                    delegatedTask.set(task);
                    log.info("Supervisor: delegated to {} with task: {}", node.getName(), task);
                    // Return confirmation — actual execution happens in the main loop
                    return "Task delegated to " + node.getDisplayName() + ". Waiting for result...";
                }

                @Override
                public String call(String toolInput, ToolContext toolContext) { return call(toolInput); }
            });
        }
        return tools;
    }

    private String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
