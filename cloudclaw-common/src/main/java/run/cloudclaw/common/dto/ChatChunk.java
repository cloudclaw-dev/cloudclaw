package run.cloudclaw.common.dto;

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

    public static ChatChunk text(String content) {
        return new ChatChunk(content, false, false, "text", null, null, null);
    }

    public static ChatChunk toolCall(String toolName, String args) {
        return new ChatChunk(args, true, false, "tool_call", toolName, null, null);
    }

    public static ChatChunk toolResult(String toolName, String result) {
        return new ChatChunk(result, true, false, "tool_result", toolName, null, null);
    }

    public static ChatChunk done() {
        return new ChatChunk(null, false, true, "done", null, null, null);
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
