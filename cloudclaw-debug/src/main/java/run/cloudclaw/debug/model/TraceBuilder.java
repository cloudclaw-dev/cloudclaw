package run.cloudclaw.debug.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutable builder for assembling a ChatTrace during observation.
 * Used by CloudClawTraceHandler to collect spans incrementally.
 */
@Getter
@Setter
public class TraceBuilder {

    private String traceId;
    private String sessionId;
    private String agentId;
    private String userId;
    private String modelId;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private int toolCallCount;
    private String status = "RUNNING";
    private String errorMessage;
    private final List<ChatSpan> spans = new ArrayList<>();

    public void addSpan(ChatSpan span) {
        spans.add(span);
    }

    public void incrementToolCalls() {
        toolCallCount++;
    }

    public ChatTrace build() {
        return ChatTrace.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(userId)
                .modelId(modelId)
                .startTime(startTime)
                .endTime(endTime)
                .durationMs(durationMs)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .toolCallCount(toolCallCount)
                .status(status)
                .errorMessage(errorMessage)
                .spans(new ArrayList<>(spans))
                .build();
    }
}
