package run.cloudclaw.debug.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A complete trace record for one chat request.
 * Contains timing, token usage, and all spans from processing.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTrace {

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
    /** RUNNING, SUCCESS, ERROR */
    private String status;
    private String errorMessage;
    private List<ChatSpan> spans;
}
