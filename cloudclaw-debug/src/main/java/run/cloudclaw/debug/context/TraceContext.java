package run.cloudclaw.debug.context;

/**
 * ThreadLocal storage for current request's business context.
 * Set at ChatEngine.chat() entry point, read by CloudClawTraceHandler.
 */
public final class TraceContext {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> AGENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> MODEL_ID = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String sessionId, String agentId, String userId, String modelId) {
        SESSION_ID.set(sessionId);
        AGENT_ID.set(agentId);
        USER_ID.set(userId);
        MODEL_ID.set(modelId);
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static String getAgentId() {
        return AGENT_ID.get();
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getModelId() {
        return MODEL_ID.get();
    }

    public static void clear() {
        SESSION_ID.remove();
        AGENT_ID.remove();
        USER_ID.remove();
        MODEL_ID.remove();
    }
}
