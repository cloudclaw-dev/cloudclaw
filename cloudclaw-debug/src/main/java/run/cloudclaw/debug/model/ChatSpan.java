package run.cloudclaw.debug.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single span within a ChatTrace, representing one step of the conversation processing.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSpan {

    /** Span type: CHAT_CLIENT, CHAT_MODEL, TOOL_CALL, ADVISOR, PROMPT_ASSEMBLE, CONTEXT_COMPRESS, AGENT_TRANSFER, MEMORY_INJECT */
    private String type;

    /** Human-readable span name */
    private String name;

    /** Span start time (ISO 8601) */
    private String startTime;

    /** Duration in milliseconds */
    private Long durationMs;

    /** Span input data (model name, arguments, etc.) */
    private Map<String, Object> input;

    /** Span output data (tokens, results, etc.) */
    private Map<String, Object> output;

    /** Span status: SUCCESS, ERROR */
    private String status;
}
