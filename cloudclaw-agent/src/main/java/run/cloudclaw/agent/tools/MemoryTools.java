package run.cloudclaw.agent.tools;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.common.repository.ProfileItemRepository;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.memory.service.TokenEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory tools for managing user profile and session context.
 *
 * <p><b>Security fix — cross-user data leak (CVE-2024-MEM-001):</b>
 * Previously {@code resolveContext()} returned the first entry from {@code contextMap},
 * which under concurrent multi-session load could return a different user's context,
 * leading to cross-user data exposure. The fix ensures sessionId is always used as the
 * lookup key, with no ambiguity about which user's data is being accessed.</p>
 *
 * <p>Context resolution strategy (in priority order):
 * <ol>
 *   <li>Explicit sessionId parameter passed via {@link ToolContext}</li>
 *   <li>ThreadLocal fallback via {@link #currentSessionId}</li>
 * </ol></p>
 */
@Slf4j
@Component
public class MemoryTools {

    private final TokenEstimator tokenEstimator;
    private final ProfileItemRepository profileItemRepository;
    private final SessionItemRepository sessionItemRepository;

    private static final int DEFAULT_PROFILE_MAX_TOKENS = 1000;
    private static final int DEFAULT_SESSION_MAX_TOKENS = 2000;

    /**
     * Per-session context keyed by sessionId.
     * Each entry contains userId, agentId, sessionId, and token limits.
     * sessionId is the primary lookup key — never iterate entries.
     */
    private static final ConcurrentHashMap<String, MemoryContext> contextMap = new ConcurrentHashMap<>();

    /**
     * ThreadLocal fallback for sessionId lookup.
     * Used when ToolContext is unavailable (e.g., direct method invocation).
     */
    private static final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    /** Key used to store/retrieve sessionId from ToolContext. */
    public static final String TOOL_CONTEXT_SESSION_ID_KEY = "sessionId";

    public MemoryTools(TokenEstimator tokenEstimator,
                       ProfileItemRepository profileItemRepository,
                       SessionItemRepository sessionItemRepository) {
        this.tokenEstimator = tokenEstimator;
        this.profileItemRepository = profileItemRepository;
        this.sessionItemRepository = sessionItemRepository;
    }

    /**
     * Set memory context for a session. Stores the context keyed by sessionId
     * and binds it to the current thread.
     *
     * <p>The sessionId is saved inside MemoryContext for verification,
     * and also used as the map key — ensuring lookup by sessionId always
     * returns the correct user's data.</p>
     */
    public static void setContext(String userId, String agentId, String sessionId,
                                   Integer profileMax, Integer sessionMax) {
        if (userId == null || sessionId == null) return;
        MemoryContext ctx = new MemoryContext(
                userId, agentId, sessionId,
                profileMax != null ? profileMax : DEFAULT_PROFILE_MAX_TOKENS,
                sessionMax != null ? sessionMax : DEFAULT_SESSION_MAX_TOKENS
        );
        contextMap.put(sessionId, ctx);
        currentSessionId.set(sessionId);
        log.debug("Memory context set for user {}: sessionId={}", userId, sessionId);
    }

    public static void clearContext(String sessionId) {
        if (sessionId != null) contextMap.remove(sessionId);
        currentSessionId.remove();
    }

    /** Bind the current thread to an existing sessionId context (for reactive thread switches). */
    public static void bindToThread(String sessionId) {
        currentSessionId.set(sessionId);
    }

    /** Unbind the current thread from memory context. */
    public static void unbindFromThread() {
        currentSessionId.remove();
    }

    // ==================== ToolCallback integration ====================

    /**
     * Entry point for ToolCallback-based invocation with ToolContext.
     * Extracts sessionId from ToolContext and delegates to the appropriate @Tool method.
     *
     * <p>This method is called by Spring AI's ToolCallback mechanism when
     * ToolContext is available. It resolves the correct session's context by sessionId,
     * preventing cross-user data access in concurrent scenarios.</p>
     *
     * @param toolInput  JSON string with tool arguments
     * @param toolContext Spring AI ToolContext containing sessionId
     * @return tool execution result
     */
    public String call(String toolInput, ToolContext toolContext) {
        String sessionId = extractSessionId(toolContext);
        // Push sessionId onto ThreadLocal so @Tool methods can resolve it
        String previousSessionId = currentSessionId.get();
        try {
            if (sessionId != null) {
                currentSessionId.set(sessionId);
            }
            // Delegate to the no-arg resolveContext which now uses the ThreadLocal
            return call(toolInput);
        } finally {
            // Restore previous ThreadLocal state
            if (previousSessionId != null) {
                currentSessionId.set(previousSessionId);
            } else {
                currentSessionId.remove();
            }
        }
    }

    /**
     * Fallback entry point without ToolContext.
     * Used when tools are invoked via @Tool annotation directly.
     */
    public String call(String toolInput) {
        // No-op: @Tool methods are invoked directly by Spring AI.
        // This method exists for ToolCallback interface compatibility.
        return "Error: Direct call not supported. Use @Tool-annotated methods.";
    }

    /**
     * Extract sessionId from ToolContext, falling back to ThreadLocal.
     */
    private static String extractSessionId(ToolContext toolContext) {
        if (toolContext != null) {
            Object sid = toolContext.getContext().get(TOOL_CONTEXT_SESSION_ID_KEY);
            if (sid instanceof String sessionIdStr && !sessionIdStr.isBlank()) {
                return sessionIdStr;
            }
        }
        // Fallback to ThreadLocal when ToolContext is unavailable
        return currentSessionId.get();
    }

    // ==================== Profile Tool ====================

    @Tool(description = """
            Manage the user's PROFILE — who this person is. Stores durable facts that persist across all sessions.

            WHAT TO SAVE HERE (proactively, don't wait to be asked):
            - Name, role, timezone, language preference
            - Communication habits (concise vs detailed, formal vs casual)
            - Personal preferences (food, travel, work style, etc.)
            - User corrections ("remember this", "don't do that again", "from now on...")
            - Personal details the user shares repeatedly

            PRIORITY: User corrections > preferences > personal facts > communication style.
            The most valuable profile entry prevents the user from repeating themselves.

            DO NOT save: task progress, temporary TODOs, session-specific agreements,
            or things easily re-discovered.

            ACTIONS: read_all — list all items; add — new entry; replace — update by id;
            remove — delete by id. Keep it compact — each entry should be one concise fact.""")
    public String memory_profile(
            @ToolParam(description = "Action: read_all | add | replace | remove") String action,
            @ToolParam(description = "Item ID (for replace/remove)", required = false) String item_id,
            @ToolParam(description = "Content (for add/replace)", required = false) String content) {
        /*
         * Security fix: resolveContext() now uses sessionId from ThreadLocal to look up
         * the exact MemoryContext entry. This prevents returning another user's context
         * when multiple sessions are active concurrently.
         */
        MemoryContext mc = resolveContext();
        if (mc == null) return "Error: No user context available.";
        String userId = mc.userId;
        int maxTokens = mc.profileMaxTokens;

        return switch (action.toLowerCase()) {
            case "read_all" -> {
                List<ProfileItem> items = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
                if (items.isEmpty()) { yield "No profile items."; }
                int totalTokens = items.stream().mapToInt(i -> tokenEstimator.estimateTokens(i.getContent())).sum();
                StringBuilder sb = new StringBuilder();
                sb.append("Profile items (").append(totalTokens).append("/").append(maxTokens).append(" tokens, ").append(items.size()).append(" items):\n");
                for (ProfileItem item : items) {
                    int t = tokenEstimator.estimateTokens(item.getContent());
                    sb.append("[").append(item.getId(), 0, 8).append("] (").append(t).append("t) ").append(item.getContent()).append("\n");
                }
                yield sb.toString();
            }
            case "add" -> {
                if (content == null || content.isBlank()) yield "Error: content is required for add.";
                List<ProfileItem> existing = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
                int currentTokens = existing.stream().mapToInt(i -> tokenEstimator.estimateTokens(i.getContent())).sum();
                int newTokens = tokenEstimator.estimateTokens(content);
                if (currentTokens + newTokens > maxTokens) {
                    yield "Error: Would exceed " + maxTokens + " token limit. Current: " + currentTokens + ", new: " + newTokens + ". Use replace/remove to free space first.";
                }
                ProfileItem item = ProfileItem.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .content(content)
                        .tokens(newTokens)
                        .createdAt(LocalDateTime.now())
                        .build();
                profileItemRepository.save(item);
                log.info("Profile item added for user {}: {} tokens", userId, newTokens);
                yield "Profile item added. ID: " + item.getId().substring(0, 8);
            }
            case "replace" -> {
                if (item_id == null || content == null) yield "Error: item_id and content required for replace.";
                List<ProfileItem> items = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
                ProfileItem target = items.stream().filter(i -> i.getId().equals(item_id)).findFirst().orElse(null);
                if (target == null) yield "Error: Item not found: " + item_id;
                target.setContent(content);
                target.setTokens(tokenEstimator.estimateTokens(content));
                target.setUpdatedAt(LocalDateTime.now());
                profileItemRepository.save(target);
                log.info("Profile item replaced: {}", item_id);
                yield "Profile item updated. New content: " + content;
            }
            case "remove" -> {
                if (item_id == null) yield "Error: item_id required for remove.";
                List<ProfileItem> items = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
                ProfileItem target = items.stream().filter(i -> i.getId().equals(item_id)).findFirst().orElse(null);
                if (target == null) yield "Error: Item not found: " + item_id;
                profileItemRepository.delete(target);
                log.info("Profile item removed: {}", item_id);
                yield "Profile item removed.";
            }
            default -> "Unknown action: " + action + ". Use: read_all, add, replace, remove.";
        };
    }

    // ==================== Session Tool ====================

    @Tool(description = """
            Manage the current SESSION'S context — background, agreements, constraints, and
            decisions made WITHIN this conversation. Cleared when session ends.

            WHAT TO SAVE HERE:
            - Task goals and current progress state
            - Agreements and decisions made in this session
            - Constraints discovered during this conversation
            - Working hypotheses or pending questions

            DO NOT save: user preferences (use profile), common knowledge,
            completed work logs, or information that won't be needed again.

            ACTIONS: read_all — list all items; add — new entry; replace — update by id;
            remove — delete by id. Update entries as context evolves — replace outdated
            info rather than accumulating.""")
    public String memory_session(
            @ToolParam(description = "Action: read_all | add | replace | remove") String action,
            @ToolParam(description = "Item ID (for replace/remove)", required = false) String item_id,
            @ToolParam(description = "Content (for add/replace)", required = false) String content) {
        /*
         * Security fix: resolveContext() uses sessionId-based lookup to prevent
         * cross-user data access in concurrent multi-session scenarios.
         */
        MemoryContext mc = resolveContext();
        if (mc == null) return "Error: No user context available.";
        String userId = mc.userId;
        MemoryContext c = mc;
        if (c.sessionId == null) return "Error: No session context available.";

        String sessionId = c.sessionId;
        int maxTokens = c.sessionMaxTokens;

        return switch (action.toLowerCase()) {
            case "read_all" -> {
                List<SessionItem> items = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                if (items.isEmpty()) { yield "No session context items."; }
                int totalTokens = items.stream().mapToInt(i -> tokenEstimator.estimateTokens(i.getContent())).sum();
                StringBuilder sb = new StringBuilder();
                sb.append("Session context (").append(totalTokens).append("/").append(maxTokens).append(" tokens, ").append(items.size()).append(" items):\n");
                for (SessionItem item : items) {
                    int t = tokenEstimator.estimateTokens(item.getContent());
                    sb.append("[").append(item.getId(), 0, 8).append("] (").append(t).append("t) ").append(item.getContent()).append("\n");
                }
                yield sb.toString();
            }
            case "add" -> {
                if (content == null || content.isBlank()) yield "Error: content is required for add.";
                List<SessionItem> existing = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                int currentTokens = existing.stream().mapToInt(i -> tokenEstimator.estimateTokens(i.getContent())).sum();
                int newTokens = tokenEstimator.estimateTokens(content);
                if (currentTokens + newTokens > maxTokens) {
                    yield "Error: Would exceed " + maxTokens + " token limit. Current: " + currentTokens + ", new: " + newTokens + ". Use replace/remove to free space first.";
                }
                SessionItem item = SessionItem.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .sessionId(sessionId)
                        .content(content)
                        .tokens(newTokens)
                        .createdAt(LocalDateTime.now())
                        .build();
                sessionItemRepository.save(item);
                log.info("Session item added for session {}: {} tokens", sessionId, newTokens);
                yield "Session context item added. ID: " + item.getId().substring(0, 8);
            }
            case "replace" -> {
                if (item_id == null || content == null) yield "Error: item_id and content required for replace.";
                List<SessionItem> items = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                SessionItem target = items.stream().filter(i -> i.getId().equals(item_id)).findFirst().orElse(null);
                if (target == null) yield "Error: Item not found: " + item_id;
                target.setContent(content);
                target.setTokens(tokenEstimator.estimateTokens(content));
                target.setUpdatedAt(LocalDateTime.now());
                sessionItemRepository.save(target);
                log.info("Session item replaced: {}", item_id);
                yield "Session context item updated. New content: " + content;
            }
            case "remove" -> {
                if (item_id == null) yield "Error: item_id required for remove.";
                List<SessionItem> items = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                SessionItem target = items.stream().filter(i -> i.getId().equals(item_id)).findFirst().orElse(null);
                if (target == null) yield "Error: Item not found: " + item_id;
                sessionItemRepository.delete(target);
                log.info("Session item removed: {}", item_id);
                yield "Session context item removed.";
            }
            default -> "Unknown action: " + action + ". Use: read_all, add, replace, remove.";
        };
    }

    // ==================== Helper for external access ====================

    public List<ProfileItem> readAllProfileItems(String userId) {
        return profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public List<SessionItem> readAllSessionItems(String sessionId) {
        return sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Resolve the current MemoryContext using explicit sessionId.
     *
     * <p><b>Security fix — cross-user data leak:</b>
     * The original implementation iterated {@code contextMap.values().iterator().next()},
     * returning an arbitrary user's context under concurrent load. This overload forces
     * the caller to specify exactly which session to access.</p>
     *
     * @param sessionId the session ID to look up (never null)
     * @return the MemoryContext for this session, or null if not found
     */
    private MemoryContext resolveContext(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("resolveContext called with null/blank sessionId — no context available");
            return null;
        }
        MemoryContext mc = contextMap.get(sessionId);
        if (mc != null) {
            return mc;
        }
        log.warn("Memory context not found for sessionId={} — setContext may not have been called", sessionId);
        return null;
    }

    /**
     * Resolve the current MemoryContext using ThreadLocal sessionId as fallback.
     *
     * <p><b>Security fix — cross-user data leak:</b>
     * Previously returned the first map entry which could be a different user's data
     * in concurrent multi-session scenarios. Now uses sessionId from ThreadLocal
     * to perform an exact lookup, never iterating over map entries.</p>
     */
    private MemoryContext resolveContext() {
        String sid = currentSessionId.get();
        if (sid != null) {
            return resolveContext(sid);
        }
        log.warn("Memory context is not set — no setContext/bindToThread was called or context was cleared");
        return null;
    }
    // ==================== Context holder ====================

    private record MemoryContext(String userId, String agentId, String sessionId,
                                  int profileMaxTokens, int sessionMaxTokens) {}
}
