package run.cloudclaw.common.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single chunk in the SSE chat stream.
 *
 * <p>Types:</p>
 * <ul>
 *   <li>{@code text} — normal text content from LLM</li>
 *   <li>{@code tool_call} — LLM is requesting to call a tool</li>
 *   <li>{@code tool_result} — tool execution result</li>
 *   <li>{@code done} — stream is complete</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatChunk {
    private String content;
    private boolean toolCall;
    private boolean done;
    /** Chunk type: text, tool_call, tool_result, done */
    @Builder.Default
    private String type = "text";
    /** Tool name (only for tool_call and tool_result types) */
    private String toolName;
    /** Tool call ID for correlating call and result */
    private String toolCallId;
    /** Context usage statistics (only in done chunk) */
    private ContextStats contextStats;
    /** Whether this chunk indicates a handoff event */
    private Boolean handoff;
    /** New session ID after handoff */
    private String newSessionId;
    /** Target agent name for handoff */
    private String targetAgent;
    /** Step index (for pipeline_step / parallel events) */
    private Integer step;
    /** Node name (for workflow events) */
    private String node;
    /** Reason (for router/handoff/supervisor events) */
    private String reason;
    /** Node list (for parallel_start event) */
    private List<String> nodes;
    /** Strategy (for parallel_merge event) */
    private String strategy;
    /** Plan (for supervisor_plan event) */
    private List<String> plan;
    /** From agent (for transfer events) */
    private String from;
    /** Error code (for error events) */
    private Integer errorCode;
    /** i18n key for frontend error localization (for error events) */
    private String errorI18nKey;
    /** Error detail message (for error events) */
    private String errorDetail;

    public static ChatChunk text(String content) {
        return new ChatChunk(content, false, false, "text", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    public static ChatChunk toolCall(String toolName, String args) {
        return new ChatChunk(args, true, false, "tool_call", toolName, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    public static ChatChunk toolResult(String toolName, String result) {
        return new ChatChunk(result, true, false, "tool_result", toolName, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    public static ChatChunk done() {
        return new ChatChunk(null, false, true, "done", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    /** Create an error event chunk with structured error information for SSE. */
    public static ChatChunk error(run.cloudclaw.common.exception.ErrorCode code, String detail) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("error")
                .errorCode(code.getCode())
                .errorI18nKey(code.getI18nKey())
                .errorDetail(detail)
                .build();
    }

    /** Create a workflow event chunk */
    public static ChatChunk workflowEvent(String type, String node, Integer step, String content) {
        return ChatChunk.builder()
                .content(content != null ? content : "")
                .toolCall(false)
                .done(false)
                .type(type)
                .node(node)
                .step(step)
                .build();
    }

    /** Create a pipeline_step event */
    public static ChatChunk pipelineStep(int step, String nodeName, String content) {
        return workflowEvent("pipeline_step", nodeName, step, content);
    }

    /** Create a parallel_start event */
    public static ChatChunk parallelStart(List<String> nodeNames) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("parallel_start")
                .nodes(nodeNames)
                .build();
    }

    /** Create a parallel_progress event */
    public static ChatChunk parallelProgress(String nodeName, String content) {
        return workflowEvent("parallel_progress", nodeName, null, content);
    }

    /** Create a parallel_complete event */
    public static ChatChunk parallelComplete(String nodeName, String content) {
        return workflowEvent("parallel_complete", nodeName, null, content);
    }

    /** Create a parallel_merge event */
    public static ChatChunk parallelMerge(String strategy) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("parallel_merge")
                .strategy(strategy)
                .build();
    }

    /** Create a router_select event */
    public static ChatChunk routerSelect(String from, String to, String reason) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("router_select")
                .from(from)
                .targetAgent(to)
                .reason(reason)
                .build();
    }

    /** Create a supervisor_plan event */
    public static ChatChunk supervisorPlan(List<String> plan) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("supervisor_plan")
                .plan(plan)
                .build();
    }

    /** Create a supervisor_delegate event */
    public static ChatChunk supervisorDelegate(String targetNode, String task) {
        return ChatChunk.builder()
                .content(task != null ? task : "")
                .toolCall(false)
                .done(false)
                .type("supervisor_delegate")
                .targetAgent(targetNode)
                .build();
    }

    /** Create a supervisor_result event */
    public static ChatChunk supervisorResult(String fromNode, String content) {
        return ChatChunk.builder()
                .content(content != null ? content : "")
                .toolCall(false)
                .done(false)
                .type("supervisor_result")
                .from(fromNode)
                .build();
    }

    /** Create a handoff event */
    public static ChatChunk handoffEvent(String from, String to, String reason) {
        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(false)
                .type("handoff")
                .from(from)
                .targetAgent(to)
                .reason(reason)
                .build();
    }

    /**
     * Context usage statistics for the current conversation.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextStats {
        /** Estimated total tokens used */
        private int totalTokens;
        /** Number of history messages */
        private int historyMessages;
        /** Number of tool calls in this turn */
        private int toolCallCount;
        /** Max context tokens for the model */
        private int maxTokens;
        /** Usage percentage (0-100) */
        private int usagePercent;
        /** Breakdown: system prompt tokens */
        private int systemTokens;
        /** Breakdown: history tokens */
        private int historyTokens;
        /** Breakdown: memory injection tokens */
        private int memoryTokens;
        /** Breakdown: current user message tokens */
        private int userMessageTokens;
        /** Breakdown: tool results tokens */
        private int toolResultTokens;
    }
}
