package run.cloudclaw.sandbox.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified context for sandbox tools.
 * Uses ConcurrentHashMap keyed by sessionId for cross-thread safety
 * (Spring AI tool callbacks execute in chatThread, not the original request thread).
 */
public class SandboxContext {

    private static final ConcurrentHashMap<String, Holder> contextMap = new ConcurrentHashMap<>();

    // Track the most recently set sessionId per thread for backward-compatible no-arg getters
    private static final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    public static void set(String sessionId, String agentId, SandboxMode mode,
                           SandboxBackend backend, String providerId) {
        contextMap.put(sessionId, new Holder(sessionId, agentId, mode, backend, providerId));
        currentSessionId.set(sessionId);
    }

    public static String getSessionId() { return fromCurrent(Holder::sessionId); }
    public static String getAgentId() { return fromCurrent(Holder::agentId); }
    public static SandboxMode getMode() { return fromCurrent(Holder::mode); }
    public static SandboxBackend getBackend() { return fromCurrent(Holder::backend); }
    public static String getProviderId() { return fromCurrent(Holder::providerId); }

    public static void clear() {
        String sid = currentSessionId.get();
        if (sid != null) {
            contextMap.remove(sid);
        }
        currentSessionId.remove();
    }

    /**
     * Bind the current thread to an existing sessionId context.
     * Used when spawning a new thread that needs access to sandbox context.
     */
    public static void bindToThread(String sessionId) {
        currentSessionId.set(sessionId);
    }

    /**
     * Unbind the current thread from sandbox context (do NOT remove the context itself).
     */
    public static void unbindFromThread() {
        currentSessionId.remove();
    }

    private static <T> T fromCurrent(java.util.function.Function<Holder, T> extractor) {
        String sid = currentSessionId.get();
        if (sid == null) return null;
        Holder h = contextMap.get(sid);
        return h != null ? extractor.apply(h) : null;
    }

    private record Holder(String sessionId, String agentId, SandboxMode mode,
                          SandboxBackend backend, String providerId) {}
}
