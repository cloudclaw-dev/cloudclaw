package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.ChatChunk;
import reactor.core.publisher.Sinks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper to bind all ThreadLocal-based contexts for a given sessionId.
 * Centralizes the repeated SandboxContext.bindToThread + MemoryTools.bindToThread calls.
 */
public final class ReactiveContextHelper {

    private static final Logger log = LoggerFactory.getLogger(ReactiveContextHelper.class);

    private ReactiveContextHelper() {}

    /**
     * Bind all thread-local contexts to the given sessionId.
     * Call this at the start of reactive callbacks (doOnNext, doOnComplete, doOnError)
     * that may execute on different threads.
     */
    public static void bindAll(String sessionId) {
        if (sessionId != null) {
            run.cloudclaw.sandbox.core.SandboxContext.bindToThread(sessionId);
            run.cloudclaw.agent.tools.MemoryTools.bindToThread(sessionId);
        }
    }

    /**
     * Unbind all thread-local contexts from the current thread.
     */
    public static void unbindAll() {
        run.cloudclaw.sandbox.core.SandboxContext.unbindFromThread();
        run.cloudclaw.agent.tools.MemoryTools.unbindFromThread();
    }

    /**
     * Safely emit a ChatChunk to a Sinks.Many, logging a warning if the emit fails
     * (e.g., due to backpressure buffer overflow or terminal state).
     */
    public static void safeEmitNext(Sinks.Many<ChatChunk> sink, ChatChunk chunk) {
        Sinks.EmitResult result = sink.tryEmitNext(chunk);
        if (result.isFailure()) {
            log.warn("Sink emit failed (result={}), dropping chunk type={}", result,
                    chunk != null ? chunk.getType() : "null");
            // Try to notify the user that output was lost
            if (chunk != null && !"error".equals(chunk.getType())) {
                sink.tryEmitNext(ChatChunk.text("[系统提示：输出缓冲区已满，部分内容丢失]"));
            }
        }
    }
}
