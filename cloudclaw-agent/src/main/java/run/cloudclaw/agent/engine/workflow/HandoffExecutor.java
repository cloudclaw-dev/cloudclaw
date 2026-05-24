package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.workflow.HandoffConfig;
import run.cloudclaw.common.dto.workflow.WorkflowDef;
import run.cloudclaw.common.dto.workflow.WorkflowNode;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.session.service.SessionService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executor;

/**
 * Handoff executor: enables multi-turn agent switching within the same session.
 * Agents can hand off to each other and back, unlike Router which is one-shot.
 *
 * <p>This executor supports a handoff chain loop: when an agent calls a
 * {@code handoff_to_xxx} tool, the target agent takes over. If the target
 * also calls a handoff tool, the chain continues. The loop runs until no
 * handoff is requested or a maximum depth is reached.</p>
 */
@Component
@Slf4j
public class HandoffExecutor {

    /** Maximum number of consecutive handoffs in a single turn to prevent infinite loops. */
    private static final int MAX_HANDOFF_DEPTH = 5;

    private final WorkflowNodeResolver nodeResolver;
    private final WorkflowChatHelper chatHelper;
    private final SessionService sessionService;
    private final Executor workflowExecutor;

    public HandoffExecutor(WorkflowNodeResolver nodeResolver,
                           WorkflowChatHelper chatHelper,
                           SessionService sessionService,
                           @Qualifier("workflowExecutor") Executor workflowExecutor) {
        this.nodeResolver = nodeResolver;
        this.chatHelper = chatHelper;
        this.sessionService = sessionService;
        this.workflowExecutor = workflowExecutor;
    }

    public Flux<ChatChunk> execute(String userId, String sessionId,
                                    String userMessage, AgentConfig config, WorkflowDef workflow) {
        Sinks.Many<ChatChunk> sink = Sinks.many().multicast().onBackpressureBuffer(256);

        List<WorkflowNode> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            sink.tryEmitComplete();
            return sink.asFlux();
        }

        List<ResolvedNodeAgent> resolvedNodes = nodeResolver.resolveAll(nodes, config);
        HandoffConfig handoffConfig = workflow.getHandoffConfig();
        boolean autoReturn = handoffConfig != null && handoffConfig.isAutoReturn();

        Runnable handoffThread = () -> {
            StringBuilder allResponses = new StringBuilder();
            List<McpSyncClient> allMcpClients = new ArrayList<>();
            try {
                // Determine current active node from session state
                String activePath = sessionService.getSession(userId, sessionId).getActiveAgentPath();
                String activeNodeId = resolveActiveNodeId(activePath);

                ResolvedNodeAgent activeNode;
                if (activeNodeId == null || "root".equals(activeNodeId)) {
                    // Use the first node as the starting node for handoff mode
                    activeNode = resolvedNodes.get(0);
                    sink.tryEmitNext(ChatChunk.handoffEvent("root", activeNode.getDisplayName(), "Starting handoff session"));
                    sessionService.updateActiveAgentPath(sessionId, activeNode.getNodeId());
                } else {
                    activeNode = resolvedNodes.stream()
                            .filter(n -> n.getNodeId().equals(activeNodeId))
                            .findFirst()
                            .orElse(resolvedNodes.get(0));
                }

                int maxToolCalls = config.getMaxToolCalls() != null ? config.getMaxToolCalls() : 50;
                int maxToolResultChars = config.getMaxToolResultChars() != null ? config.getMaxToolResultChars() : 3000;

                // Handoff loop — supports multiple consecutive handoffs
                for (int depth = 0; depth < MAX_HANDOFF_DEPTH; depth++) {
                    // Build system prompt for active node using PromptAssembler
                    String systemPrompt = chatHelper.buildNodeSystemPrompt(activeNode, config, userId, sessionId, userMessage);
                    systemPrompt += "\n\n" + buildHandoffHint(activeNode, resolvedNodes, autoReturn);

                    // Resolve tools for active node
                    List<ToolCallback> nodeTools = new ArrayList<>();
                    AgentConfig nodeConfig = chatHelper.buildNodeConfig(config, activeNode);
                    List<McpSyncClient> mcpClients = new ArrayList<>();
                    nodeTools.addAll(chatHelper.resolveToolCallbacks(nodeConfig, mcpClients));
                    allMcpClients.addAll(mcpClients);

                    // Build handoff tools for active node
                    AtomicReference<String> handoffTarget = new AtomicReference<>(null);
                    AtomicReference<String> handoffReason = new AtomicReference<>("");
                    List<ToolCallback> handoffTools = buildHandoffTools(resolvedNodes, activeNode, handoffTarget, handoffReason);
                    nodeTools.addAll(handoffTools);

                    List<ToolCallback> truncatedTools = nodeTools.stream()
                            .map(cb -> (ToolCallback) new run.cloudclaw.agent.engine.TruncatingToolCallback(cb, maxToolResultChars))
                            .toList();

                    // Stream LLM response from active node
                    WorkflowChatHelper.LogContext logCtx = new WorkflowChatHelper.LogContext(
                            sessionId, config.getAgentId().toString(), userId, activeNode.getDisplayName());
                    Flux<String> stream = chatHelper.streamLlmWithTools(
                            activeNode.getModelId(), systemPrompt, userMessage, truncatedTools, maxToolCalls, logCtx);

                    StringBuilder response = new StringBuilder();
                    stream.doOnNext(chunk -> {
                        response.append(chunk);
                        sink.tryEmitNext(ChatChunk.text(chunk));
                    }).blockLast();

                    allResponses.append(response.toString());

                    // Check if handoff occurred
                    String targetNodeId = handoffTarget.get();
                    if (targetNodeId == null) {
                        // No handoff — agent finished its response
                        log.info("Handoff: {} completed without further handoff (depth={})", activeNode.getName(), depth);
                        break;
                    }

                    // Find the target node
                    ResolvedNodeAgent targetNode = resolvedNodes.stream()
                            .filter(n -> n.getNodeId().equals(targetNodeId))
                            .findFirst().orElseThrow();

                    // Emit handoff SSE event
                    sink.tryEmitNext(ChatChunk.handoffEvent(activeNode.getDisplayName(),
                            targetNode.getDisplayName(), handoffReason.get()));

                    // Save transfer system message to conversation history
                    Message transferMsg = new Message();
                    transferMsg.setSessionId(UUID.fromString(sessionId));
                    transferMsg.setRole("system");
                    transferMsg.setContent("[Handoff: " + activeNode.getDisplayName() + " → " + targetNode.getDisplayName()
                            + ", reason=" + handoffReason.get() + "]");
                    sessionService.saveMessage(transferMsg);

                    // Update session active path
                    sessionService.updateActiveAgentPath(sessionId, targetNodeId);
                    log.info("Handoff: {} → {} (reason: {}, depth={})",
                            activeNode.getName(), targetNode.getName(), handoffReason.get(), depth);

                    // Switch to target node for next iteration
                    activeNode = targetNode;
                }

                // Auto-return to root if configured and current agent is not root
                if (autoReturn) {
                    sessionService.updateActiveAgentPath(sessionId, "root");
                    log.info("Handoff: auto-return to root");
                }

                // Done
                sink.tryEmitNext(chatHelper.buildDoneChunk(config, userMessage, allResponses.toString()));
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Handoff execution error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            } finally {
                // Close all MCP clients
                for (McpSyncClient client : allMcpClients) {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        };

        workflowExecutor.execute(handoffThread);

        return sink.asFlux();
    }

    private String resolveActiveNodeId(String activePath) {
        if (activePath == null || "root".equals(activePath)) return null;
        // activePath format: "node_1" or "root/node_1"
        String path = activePath.contains("/") ? activePath.substring(activePath.lastIndexOf('/') + 1) : activePath;
        return path;
    }

    private String buildHandoffHint(ResolvedNodeAgent activeNode, List<ResolvedNodeAgent> allNodes,
                                     boolean autoReturn) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Handoff Mode\n\n");
        sb.append("You are currently active as **").append(activeNode.getDisplayName()).append("**.\n\n");
        sb.append("### Available agents for handoff:\n\n");
        for (ResolvedNodeAgent node : allNodes) {
            if (!node.getNodeId().equals(activeNode.getNodeId())) {
                sb.append("- **").append(node.getName()).append("**");
                if (node.getDescription() != null) sb.append(": ").append(node.getDescription());
                sb.append("\n");
            }
        }
        sb.append("\nUse `handoff_to_xxx` tools to transfer to another agent when appropriate.\n");
        if (autoReturn) {
            sb.append("After completing your task, you will automatically return to the parent agent.\n");
        }
        return sb.toString();
    }

    private List<ToolCallback> buildHandoffTools(List<ResolvedNodeAgent> nodes, ResolvedNodeAgent activeNode,
                                                  AtomicReference<String> handoffTarget,
                                                  AtomicReference<String> handoffReason) {
        List<ToolCallback> tools = new ArrayList<>();

        // Handoff to other agents
        for (ResolvedNodeAgent node : nodes) {
            if (node.getNodeId().equals(activeNode.getNodeId())) continue;
            String toolName = "handoff_to_" + toSnakeCase(node.getName());
            String baseDesc = "Hand off to " + node.getDisplayName();
            String nodeDesc = node.getDescription();
            String toolDescription = nodeDesc != null ? baseDesc + ". " + nodeDesc : baseDesc;
            final String nodeId = node.getNodeId();

            tools.add(new ToolCallback() {
                private final ToolDefinition toolDef = ToolDefinition.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\",\"description\":\"Why this handoff is needed\"}},\"required\":[\"reason\"]}")
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
                    handoffTarget.set(nodeId);
                    handoffReason.set(reason);
                    log.info("Handoff: {} → {} (reason: {})", activeNode.getName(), node.getName(), reason);
                    return "HANDOFF:" + node.getName() + ":" + nodeId + ":" + reason;
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
