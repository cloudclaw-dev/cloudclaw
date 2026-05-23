package run.cloudclaw.memory.injector;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.common.repository.ProfileItemRepository;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.memory.service.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryInjector {

    private final ProfileItemRepository profileItemRepository;
    private final SessionItemRepository sessionItemRepository;
    private final TokenEstimator tokenEstimator;

    private static final int DEFAULT_MAX_MEMORY_TOKENS = 3000;

    /**
     * Build memory context string to inject into system prompt.
     * @param userId    user ID
     * @param sessionId session ID (nullable)
     * @param agentId   agent ID (unused, reserved)
     * @param userMessage current user message (unused, reserved for relevance filtering)
     * @return formatted memory context string, or empty string if no memories
     */
    public String buildMemoryContext(String userId, String sessionId, String agentId, String userMessage) {
        return buildMemoryContext(userId, sessionId, DEFAULT_MAX_MEMORY_TOKENS);
    }

    /**
     * Build memory context with custom max token budget.
     */
    public String buildMemoryContext(String userId, String sessionId, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int totalTokens = 0;

        // 1. User profile
        try {
            List<ProfileItem> profileItems = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!profileItems.isEmpty()) {
                if (totalTokens == 0) {
                    sb.append("## Memory\n");
                    sb.append("以下是从过往对话中积累的关于该用户的信息。利用这些信息提供个性化服务，避免让用户重复已告知过你的事情。\n\n");
                }
                sb.append("### User Profile\n");
                for (ProfileItem item : profileItems) {
                    int t = tokenEstimator.estimateTokens(item.getContent());
                    if (totalTokens + t > maxTokens) break;
                    sb.append("- ").append(item.getContent()).append("\n");
                    totalTokens += t;
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("Failed to load profile: {}", e.getMessage());
        }

        // 2. Session context
        if (sessionId != null) {
            try {
                List<SessionItem> sessionItems = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
                if (!sessionItems.isEmpty()) {
                    sb.append("### Session Context\n");
                    for (SessionItem item : sessionItems) {
                        int t = tokenEstimator.estimateTokens(item.getContent());
                        if (totalTokens + t > maxTokens) break;
                        sb.append("- ").append(item.getContent()).append("\n");
                        totalTokens += t;
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to load session context: {}", e.getMessage());
            }
        }

        log.info("Memory context injected: {} tokens", totalTokens);
        return sb.toString();
    }
}
