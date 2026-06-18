package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.RouterConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.agent.engine.ReactiveContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.model.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executor;

/**
 * Router executor: uses LLM to select one sub-agent to handle the request.
 * One-shot routing — once selected, the sub-agent processes the request and returns.
 *
 * <p>The router builds {@code route_to_xxx} tools from the workflow nodes and passes them
 * to the LLM. The LLM picks a route by calling one of these tools. If no route is selected
 * and {@code allowFallback=true}, the LLM's direct response is returned.</p>
 */
@Component
@Slf4j
public class RouterExecutor {

    private final WorkflowNodeResolver nodeResolver;
    private final WorkflowChatHelper chatHelper;
    private final Executor workflowExecutor;

    public RouterExecutor(WorkflowNodeResolver nodeResolver,
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

        RouterConfig routerConfig = workflow.getRouterConfig();
        boolean allowFallback = routerConfig == null || routerConfig.isAllowFallback();

        Runnable routerThread = () -> {
            List<McpSyncClient> mcpClients = new ArrayList<>();
            try {
                // Build routing system prompt
                String routerPrompt = buildRouterPrompt(config, resolvedNodes);

                // Build routing tools — these MUST be passed to the LLM call
                AtomicReference<String> selectedNodeId = new AtomicReference<>(null);
                AtomicReference<String> selectedReason = new AtomicReference<>("");
                List<ToolCallback> routingTools = buildRoutingTools(resolvedNodes, selectedNodeId, selectedReason);

                // Call LLM for routing decision WITH tools (critical: tools must be passed)
                int maxToolCalls = config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50;
                WorkflowChatHelper.LogContext routerLogCtx = new WorkflowChatHelper.LogContext(
                        sessionId, config.getAgentId().toString(), userId, "router");
                String routingResponse;
                try {
                    routingResponse = chatHelper.callLlmWithTools(config.getModelId(), routerPrompt,
                            userMessage, routingTools, maxToolCalls, history, routerLogCtx);
                } catch (Exception e) {
                    log.warn("Router LLM call with tools failed, falling back to direct: {}", e.getMessage());
                    routingResponse = chatHelper.callLlm(config.getModelId(), routerPrompt, userMessage, history, routerLogCtx);
                }

                String finalResponse;

                // Check if routing happened via tool call
                String targetNodeId = selectedNodeId.get();
                if (targetNodeId == null) {
                    // No routing tool was called — LLM responded directly
                    if (allowFallback) {
                        log.info("Router: no route selected, using fallback (direct response)");
                        ReactiveContextHelper.safeEmitNext(sink, ChatChunk.text(routingResponse));
                        finalResponse = routingResponse;
                    } else {
                        // No fallback — route to the first node as default
                        ResolvedNodeAgent firstNode = resolvedNodes.get(0);
                        selectedNodeId.set(firstNode.getNodeId());
                        selectedReason.set("No routing decision made, defaulting to first agent");
                        log.info("Router: no route selected, defaulting to node {}", firstNode.getNodeId());
                        finalResponse = null;
                    }
                } else {
                    finalResponse = null;
                }

                if (selectedNodeId.get() != null) {
                    String resolvedTargetNodeId = selectedNodeId.get();
                    // Find and execute the selected node
                    ResolvedNodeAgent selectedNode = resolvedNodes.stream()
                            .filter(n -> n.getNodeId().equals(resolvedTargetNodeId))
                            .findFirst()
                            .orElseThrow();

                    ReactiveContextHelper.safeEmitNext(sink, ChatChunk.routerSelect("root", selectedNode.getDisplayName(), selectedReason.get()));

                    // Build prompt for selected node using PromptAssembler
                    String nodePrompt = chatHelper.buildNodeSystemPrompt(selectedNode, config, userId, sessionId, userMessage);

                    // Resolve tools for selected node
                    AgentConfig nodeConfig = chatHelper.buildNodeConfig(config, selectedNode);
                    List<ToolCallback> nodeTools = new ArrayList<>(chatHelper.resolveToolCallbacks(nodeConfig, mcpClients));

                    int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;
                    List<ToolCallback> truncatedTools = nodeTools.stream()
                            .map(cb -> (ToolCallback) new run.cloudclaw.agent.engine.TruncatingToolCallback(cb, maxToolResultChars))
                            .toList();

                    // Stream the selected node's response
                    WorkflowChatHelper.LogContext nodeLogCtx = new WorkflowChatHelper.LogContext(
                            sessionId, config.getAgentId().toString(), userId, selectedNode.getDisplayName());
                    Flux<String> stream = chatHelper.streamLlmWithTools(
                            selectedNode.getModelId(), nodePrompt, userMessage, truncatedTools, maxToolCalls, history, nodeLogCtx);

                    StringBuilder response = new StringBuilder();
                    stream.doOnNext(chunk -> {
                        response.append(chunk);
                        ReactiveContextHelper.safeEmitNext(sink, ChatChunk.text(chunk));
                    }).blockLast(Duration.ofMinutes(5));

                    finalResponse = response.toString();
                }

                // Done
                ReactiveContextHelper.safeEmitNext(sink, chatHelper.buildDoneChunk(config, userMessage,
                        finalResponse != null ? finalResponse : ""));
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Router execution error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            } finally {
                for (McpSyncClient client : mcpClients) {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        };

        workflowExecutor.execute(routerThread);

        return sink.asFlux();
    }

    private String buildRouterPrompt(AgentConfig config, List<ResolvedNodeAgent> nodes) {
        StringBuilder sb = new StringBuilder();
        if (config.getSystemPrompt() != null) {
            sb.append(config.getSystemPrompt()).append("\n\n");
        }
        sb.append("## Router Mode\n\n");
        sb.append("You are a router agent. Analyze the user's request and select the most appropriate sub-agent to handle it.\n\n");
        sb.append("### Available Agents:\n\n");
        for (ResolvedNodeAgent node : nodes) {
            sb.append("- **").append(node.getName()).append("**");
            if (node.getDescription() != null && !node.getDescription().isBlank()) {
                sb.append(": ").append(node.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\nCall the appropriate `route_to_xxx` tool to select an agent. ");
        sb.append("Only select one agent. If no agent matches, respond directly.\n");
        return sb.toString();
    }

    private List<ToolCallback> buildRoutingTools(List<ResolvedNodeAgent> nodes,
                                                  AtomicReference<String> selectedNodeId,
                                                  AtomicReference<String> selectedReason) {
        List<ToolCallback> tools = new ArrayList<>();
        for (ResolvedNodeAgent node : nodes) {
            String toolName = "route_to_" + toSnakeCase(node.getName());
            String baseDesc = "Route to " + node.getDisplayName();
            String nodeDesc = node.getDescription();
            String toolDescription = nodeDesc != null ? baseDesc + ". " + nodeDesc : baseDesc;
            final String nodeId = node.getNodeId();
            tools.add(new ToolCallback() {
                private final ToolDefinition toolDef = ToolDefinition.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"description\":\"Why this agent is selected\"}},\"required\":[\"reason\"]}")
                        .build();

                @Override
                public ToolDefinition getToolDefinition() { return toolDef; }

                @Override
                public String call(String toolInput) {
                    String reason = "";
                    try {
                        if (toolInput != null && !toolInput.isBlank()) {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(toolInput);
                            if (jsonNode.has("reason")) reason = jsonNode.get("reason").asText();
                        }
                    } catch (Exception e) {
                        reason = toolInput != null ? toolInput : "";
                    }
                    selectedNodeId.set(nodeId);
                    selectedReason.set(reason);
                    log.info("Router: selected node {} ({}) reason: {}", nodeId, node.getName(), reason);
                    return "Routed to " + node.getDisplayName() + ". You are now handling this request.";
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
