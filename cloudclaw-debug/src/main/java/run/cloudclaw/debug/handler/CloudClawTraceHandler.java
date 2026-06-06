package run.cloudclaw.debug.handler;

import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import run.cloudclaw.debug.config.DebugProperties;
import run.cloudclaw.debug.context.TraceContext;
import run.cloudclaw.debug.event.DebugEventBus;
import run.cloudclaw.debug.model.ChatSpan;
import run.cloudclaw.debug.model.TraceBuilder;
import run.cloudclaw.debug.model.ChatTrace;
import run.cloudclaw.debug.repository.ChatTraceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Core ObservationHandler that intercepts Spring AI's ChatClient/ChatModel/ToolCall
 * observations and records them as ChatTrace/ChatSpan for debug analysis.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cloudclaw.debug.enabled", havingValue = "true")
public class CloudClawTraceHandler implements ObservationHandler<Observation.Context> {

    private final ChatTraceRepository traceRepository;
    private final DebugProperties props;
    private final DebugEventBus eventBus;

    /** Active traces keyed by a stable trace ID derived from the ThreadLocal context. */
    private final ConcurrentHashMap<String, TraceBuilder> activeTraces = new ConcurrentHashMap<>();

    /** Span start times keyed by context identity hash code. */
    private final ConcurrentHashMap<Integer, Instant> spanStartTimes = new ConcurrentHashMap<>();

    public CloudClawTraceHandler(ChatTraceRepository traceRepository,
                                 DebugProperties props,
                                 DebugEventBus eventBus) {
        this.traceRepository = traceRepository;
        this.props = props;
        this.eventBus = eventBus;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatClientObservationContext
                || context instanceof ChatModelObservationContext
                || context instanceof ToolCallingObservationContext;
    }

    @Override
    public void onStart(Observation.Context context) {
        spanStartTimes.put(System.identityHashCode(context), Instant.now());

        if (context instanceof ChatClientObservationContext) {
            // Top-level ChatClient call: create a new Trace
            String sessionId = TraceContext.getSessionId();
            if (sessionId == null) {
                return; // No business context, skip tracing
            }
            String traceId = sessionId + "-" + System.nanoTime();
            TraceBuilder builder = new TraceBuilder();
            builder.setTraceId(traceId);
            builder.setSessionId(sessionId);
            builder.setAgentId(TraceContext.getAgentId());
            builder.setUserId(TraceContext.getUserId());
            builder.setModelId(TraceContext.getModelId());
            builder.setStartTime(Instant.now());
            activeTraces.put(traceId, builder);
        }
    }

    @Override
    public void onStop(Observation.Context context) {
        Instant start = spanStartTimes.remove(System.identityHashCode(context));
        long durationMs = start != null ? Duration.between(start, Instant.now()).toMillis() : 0;

        if (context instanceof ToolCallingObservationContext tc) {
            TraceBuilder builder = getActiveTrace();
            if (builder != null) {
                String toolName = tc.getToolDefinition().name();

                Map<String, Object> input = new HashMap<>();
                input.put("toolName", toolName);
                String args = tc.getToolCallArguments();
                if (args != null) {
                    input.put("arguments", truncate(args, props.getMaxResultLength()));
                }

                Map<String, Object> output = new HashMap<>();
                String result = tc.getToolCallResult();
                if (result != null) {
                    output.put("result", truncate(result, props.getMaxResultLength()));
                    output.put("resultLength", result.length());
                }

                builder.addSpan(ChatSpan.builder()
                        .type("TOOL_CALL")
                        .name(toolName)
                        .startTime(start != null ? start.toString() : Instant.now().toString())
                        .durationMs(durationMs)
                        .input(input)
                        .output(output)
                        .status("SUCCESS")
                        .build());
                builder.incrementToolCalls();

                // Publish real-time debug event for SSE
                eventBus.publish(builder.getSessionId(), Map.of(
                        "spanType", "TOOL_CALL",
                        "toolName", toolName,
                        "durationMs", durationMs
                ));
            }
        }

        if (context instanceof ChatModelObservationContext cm) {
            TraceBuilder builder = getActiveTrace();
            if (builder != null) {
                // Extract high-cardinality key values from the observation context
                Map<String, Object> attrs = new HashMap<>();
                cm.getHighCardinalityKeyValues().forEach(kv ->
                        attrs.put(kv.getKey(), kv.getValue())
                );

                Map<String, Object> input = new HashMap<>();
                input.put("model", attrs.get("gen_ai.request.model"));

                Map<String, Object> output = new HashMap<>();
                output.put("inputTokens", attrs.get("gen_ai.usage.input_tokens"));
                output.put("outputTokens", attrs.get("gen_ai.usage.output_tokens"));
                output.put("totalTokens", attrs.get("gen_ai.usage.total_tokens"));
                output.put("finishReason", attrs.get("gen_ai.response.finish_reasons"));

                // Optional: prompt and completion content if available
                String prompt = (String) attrs.get("gen_ai.prompt");
                if (prompt != null) {
                    input.put("prompt", truncate(prompt, props.getMaxPromptLength()));
                }
                String completion = (String) attrs.get("gen_ai.completion");
                if (completion != null) {
                    output.put("completion", truncate(completion, props.getMaxPromptLength()));
                }

                builder.addSpan(ChatSpan.builder()
                        .type("CHAT_MODEL")
                        .name("ChatModel")
                        .startTime(start != null ? start.toString() : Instant.now().toString())
                        .durationMs(durationMs)
                        .input(input)
                        .output(output)
                        .status("SUCCESS")
                        .build());

                // Update token counts
                builder.setInputTokens(parseIntSafe(attrs.get("gen_ai.usage.input_tokens")));
                builder.setOutputTokens(parseIntSafe(attrs.get("gen_ai.usage.output_tokens")));
            }
        }

        if (context instanceof ChatClientObservationContext) {
            // Top-level call completed: persist the entire trace
            TraceBuilder builder = getActiveTrace();
            if (builder != null) {
                builder.setEndTime(Instant.now());
                builder.setDurationMs(durationMs);
                builder.setStatus("SUCCESS");

                ChatTrace trace = builder.build();
                traceRepository.saveAsync(trace);
                activeTraces.remove(builder.getTraceId());
            }
        }
    }

    @Override
    public void onError(Observation.Context context) {
        Instant start = spanStartTimes.remove(System.identityHashCode(context));
        long durationMs = start != null ? Duration.between(start, Instant.now()).toMillis() : 0;

        TraceBuilder builder = getActiveTrace();
        if (builder != null) {
            builder.setStatus("ERROR");
            builder.setErrorMessage(context.getError() != null
                    ? context.getError().getMessage() : "Unknown error");

            // If this is the top-level ChatClient, persist immediately
            if (context instanceof ChatClientObservationContext) {
                builder.setEndTime(Instant.now());
                builder.setDurationMs(durationMs);
                ChatTrace trace = builder.build();
                traceRepository.saveAsync(trace);
                activeTraces.remove(builder.getTraceId());
            }
        }
    }

    /**
     * Get the current thread's active TraceBuilder.
     * Uses the TraceContext sessionId to find the matching trace.
     */
    private TraceBuilder getActiveTrace() {
        String sessionId = TraceContext.getSessionId();
        if (sessionId != null) {
            // Find by session ID prefix in trace ID
            for (Map.Entry<String, TraceBuilder> entry : activeTraces.entrySet()) {
                if (entry.getKey().startsWith(sessionId)) {
                    return entry.getValue();
                }
            }
        }
        // Fallback: return any active trace
        return activeTraces.values().stream().findFirst().orElse(null);
    }

    /**
     * Get the current thread's active TraceBuilder for external use (e.g., CustomSpanRecorder).
     */
    public TraceBuilder getCurrentTrace() {
        return getActiveTrace();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...[truncated, total=" + text.length() + "]";
    }

    private int parseIntSafe(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
