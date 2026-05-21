package run.cloudclaw.sandbox.core;

/**
 * Unified ThreadLocal context for sandbox tools.
 * Set once per request, cleared after conversation.
 */
public class SandboxContext {

    private static final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private static final ThreadLocal<String> agentId = new ThreadLocal<>();
    private static final ThreadLocal<SandboxMode> mode = new ThreadLocal<>();
    private static final ThreadLocal<SandboxBackend> backend = new ThreadLocal<>();
    private static final ThreadLocal<String> providerId = new ThreadLocal<>();

    public static void set(String sessionId, String agentId, SandboxMode mode,
                           SandboxBackend backend, String providerId) {
        SandboxContext.sessionId.set(sessionId);
        SandboxContext.agentId.set(agentId);
        SandboxContext.mode.set(mode);
        SandboxContext.backend.set(backend);
        SandboxContext.providerId.set(providerId);
    }

    public static String getSessionId() { return sessionId.get(); }
    public static String getAgentId() { return agentId.get(); }
    public static SandboxMode getMode() { return mode.get(); }
    public static SandboxBackend getBackend() { return backend.get(); }
    public static String getProviderId() { return providerId.get(); }

    public static void clear() {
        sessionId.remove();
        agentId.remove();
        mode.remove();
        backend.remove();
        providerId.remove();
    }
}
