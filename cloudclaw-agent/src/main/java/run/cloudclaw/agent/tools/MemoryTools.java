package run.cloudclaw.agent.tools;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.common.repository.ProfileItemRepository;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.memory.service.TokenEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MemoryTools {

    private final TokenEstimator tokenEstimator;
    private final ProfileItemRepository profileItemRepository;
    private final SessionItemRepository sessionItemRepository;

    private static final int DEFAULT_PROFILE_MAX_TOKENS = 1000;
    private static final int DEFAULT_SESSION_MAX_TOKENS = 2000;

    /** Shared context map — keyed by userId, safe for cross-thread access (SSE streaming). */
    private static final ConcurrentHashMap<String, MemoryContext> contextMap = new ConcurrentHashMap<>();

    public MemoryTools(TokenEstimator tokenEstimator,
                       ProfileItemRepository profileItemRepository,
                       SessionItemRepository sessionItemRepository) {
        this.tokenEstimator = tokenEstimator;
        this.profileItemRepository = profileItemRepository;
        this.sessionItemRepository = sessionItemRepository;
    }

    public static void setContext(String userId, String agentId, String sessionId,
                                   Integer profileMax, Integer sessionMax) {
        if (userId == null) return;
        contextMap.put(userId, new MemoryContext(
                userId, agentId, sessionId,
                profileMax != null ? profileMax : DEFAULT_PROFILE_MAX_TOKENS,
                sessionMax != null ? sessionMax : DEFAULT_SESSION_MAX_TOKENS
        ));
        log.debug("Memory context set for user {}: sessionId={}", userId, sessionId);
    }

    public static void clearContext(String userId) {
        if (userId != null) {
            contextMap.remove(userId);
        }
    }

    private MemoryContext ctx(String userId) {
        MemoryContext c = contextMap.get(userId);
        if (c == null) {
            log.warn("No memory context found for userId: {}", userId);
        }
        return c;
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
        // Resolve userId from context map
        String userId = resolveUserId();
        if (userId == null) return "Error: No user context available.";

        MemoryContext c = ctx(userId);
        int maxTokens = (c != null) ? c.profileMaxTokens : DEFAULT_PROFILE_MAX_TOKENS;

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
                ProfileItem target = items.stream().filter(i -> i.getId().startsWith(item_id) || i.getId().equals(item_id)).findFirst().orElse(null);
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
                ProfileItem target = items.stream().filter(i -> i.getId().startsWith(item_id) || i.getId().equals(item_id)).findFirst().orElse(null);
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
        String userId = resolveUserId();
        if (userId == null) return "Error: No user context available.";

        MemoryContext c = ctx(userId);
        if (c == null || c.sessionId == null) return "Error: No session context available.";

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
                SessionItem target = items.stream().filter(i -> i.getId().startsWith(item_id) || i.getId().equals(item_id)).findFirst().orElse(null);
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
                SessionItem target = items.stream().filter(i -> i.getId().startsWith(item_id) || i.getId().equals(item_id)).findFirst().orElse(null);
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
     * Resolve userId from context map.
     * Tries to find any context entry — since most deployments have one user at a time,
     * falling back to the first available entry is reasonable.
     */
    private String resolveUserId() {
        // First try: get all contexts and find one
        if (contextMap.isEmpty()) {
            log.warn("Memory context map is empty — no setContext was called or context was cleared");
            return null;
        }
        // If only one user, use that
        if (contextMap.size() == 1) {
            return contextMap.keys().nextElement();
        }
        // Multiple users — can't determine which one without more info
        // This shouldn't happen in normal usage
        log.warn("Multiple users in memory context map: {}", contextMap.keySet());
        return contextMap.keys().nextElement();
    }

    // ==================== Context holder ====================

    private record MemoryContext(String userId, String agentId, String sessionId,
                                  int profileMaxTokens, int sessionMaxTokens) {}
}
