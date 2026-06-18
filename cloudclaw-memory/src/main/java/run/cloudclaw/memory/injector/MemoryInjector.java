package run.cloudclaw.memory.injector;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.common.repository.ProfileItemRepository;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.memory.service.TokenEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * @return formatted memory context string, or empty string if no memories
     */
    public String buildMemoryContext(String userId, String sessionId, String agentId, String userMessage) {
        return buildMemoryContextWithRefs(userId, sessionId, DEFAULT_MAX_MEMORY_TOKENS).getContent();
    }

    /**
     * Build memory context with custom max token budget.
     */
    public String buildMemoryContext(String userId, String sessionId, int maxTokens) {
        return buildMemoryContextWithRefs(userId, sessionId, maxTokens).getContent();
    }

    /**
     * Build memory context with references to included items.
     * Returns both the formatted text and the list of items actually included.
     */
    public MemoryContext buildMemoryContextWithRefs(String userId, String sessionId, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int totalTokens = 0;
        List<ProfileItem> includedProfile = new ArrayList<>();
        List<SessionItem> includedSession = new ArrayList<>();

        // 1. User profile
        try {
            List<ProfileItem> profileItems = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
            if (!profileItems.isEmpty()) {
                if (totalTokens == 0) {
                    sb.append("## Memory\n");
                    sb.append("\u4ee5\u4e0b\u662f\u4ece\u8fc7\u5f80\u5bf9\u8bdd\u4e2d\u79ef\u7d2f\u7684\u5173\u4e8e\u8be5\u7528\u6237\u7684\u4fe1\u606f\u3002\u5229\u7528\u8fd9\u4e9b\u4fe1\u606f\u63d0\u4f9b\u4e2a\u6027\u5316\u670d\u52a1\uff0c\u907f\u514d\u8ba9\u7528\u6237\u91cd\u590d\u5df2\u544a\u77e5\u8fc7\u4f60\u7684\u4e8b\u60c5\u3002\n\n");
                }
                sb.append("### User Profile\n");
                for (ProfileItem item : profileItems) {
                    int t = tokenEstimator.estimateTokens(item.getContent());
                    if (totalTokens + t > maxTokens) break;
                    sb.append("- ").append(item.getContent()).append("\n");
                    totalTokens += t;
                    includedProfile.add(item);
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
                        includedSession.add(item);
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to load session context: {}", e.getMessage());
            }
        }

        log.info("Memory context injected: {} tokens ({} profile, {} session)", totalTokens, includedProfile.size(), includedSession.size());
        return new MemoryContext(sb.toString(), includedProfile, includedSession);
    }
}
