package run.cloudclaw.debug.recorder;

import run.cloudclaw.debug.handler.CloudClawTraceHandler;
import run.cloudclaw.debug.model.ChatSpan;
import run.cloudclaw.debug.model.TraceBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility for recording CloudClaw-specific custom spans (Prompt Assemble,
 * Context Compress, Agent Transfer, Memory Inject) outside of Spring AI's
 * built-in observation.
 *
 * <p>If debug mode is disabled or no active trace exists, the action runs
 * normally without recording.</p>
 */
public class CustomSpanRecorder {

    private static volatile boolean debugEnabled = false;
    private static volatile CloudClawTraceHandler traceHandler;

    private CustomSpanRecorder() {}

    /**
     * Initialize the recorder with the trace handler reference.
     */
    public static void init(boolean enabled, CloudClawTraceHandler handler) {
        debugEnabled = enabled;
        traceHandler = handler;
    }

    /**
     * Record a custom span around the given action.
     * The action is always executed; the span is only recorded if debug mode is enabled
     * and there is an active trace.
     *
     * @param type   Span type (e.g., PROMPT_ASSEMBLE, CONTEXT_COMPRESS)
     * @param action The action to execute; returns a map of output attributes
     */
    public static Map<String, Object> record(String type, Supplier<Map<String, Object>> action) {
        if (!debugEnabled || traceHandler == null) {
            return action.get();
        }

        long start = System.currentTimeMillis();
        Map<String, Object> attrs = action.get();
        long duration = System.currentTimeMillis() - start;

        TraceBuilder builder = traceHandler.getCurrentTrace();
        if (builder != null) {
            builder.addSpan(ChatSpan.builder()
                    .type(type)
                    .name(type)
                    .startTime(Instant.ofEpochMilli(start).toString())
                    .durationMs(duration)
                    .output(attrs)
                    .status("SUCCESS")
                    .build());
        }

        return attrs;
    }
}
