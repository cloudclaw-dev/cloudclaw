package run.cloudclaw.agent.engine;

import run.cloudclaw.common.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically compresses conversation context before each LLM call.
 *
 * <p>When the estimated token count exceeds a threshold (default 75% of context window),
 * older messages are dropped while keeping summary messages and recent turns intact.</p>
 *
 * <p>This is a real-time, non-persistent operation — it only affects what's sent to the LLM,
 * not what's stored in the database.</p>
 */
@Component
@Slf4j
public class ContextCompressor {

    /** Use 75% of context window as the threshold for triggering compression */
    private static final double USAGE_THRESHOLD = 0.75;

    /**
     * Compress context to fit within model's context window.
     *
     * @param messages       loaded messages (summary + non-compressed)
     * @param systemPrompt   the system prompt
     * @param userMessage    the current user message
     * @param contextWindow  model's max context window in tokens
     * @return possibly trimmed list of messages
     */
    public List<Message> compress(List<Message> messages, String systemPrompt,
                                   String userMessage, int contextWindow,
                                   Double usageThreshold) {
        double thresholdRatio = usageThreshold != null ? usageThreshold : USAGE_THRESHOLD;
        int systemTokens = estimateTokens(systemPrompt);
        int userTokens = estimateTokens(userMessage);
        // Reserve some tokens for the response (25% of remaining)
        int reservedForResponse = (int) ((contextWindow - systemTokens - userTokens) * 0.25);
        int budgetForHistory = contextWindow - systemTokens - userTokens - reservedForResponse;
        int threshold = (int) (contextWindow * thresholdRatio) - systemTokens - userTokens;

        if (threshold <= 0) {
            log.warn("System prompt + user message already near context limit (sys={}, user={})",
                    systemTokens, userTokens);
            return messages;
        }

        // Calculate total history tokens
        int historyTokens = messages.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();

        if (historyTokens <= threshold) {
            log.debug("History tokens {} within threshold {}, no compression needed",
                    historyTokens, threshold);
            return messages;
        }

        log.info("History tokens {} exceeds threshold {}, starting dynamic compression",
                historyTokens, threshold);

        // Strategy: keep summary messages + trim oldest non-summary messages
        List<Message> summaries = messages.stream()
                .filter(m -> "summary".equals(m.getRole()) || Boolean.TRUE.equals(m.getIsSummary()))
                .toList();

        List<Message> nonSummaries = messages.stream()
                .filter(m -> !"summary".equals(m.getRole()) && !Boolean.TRUE.equals(m.getIsSummary()))
                .toList();

        // Calculate tokens used by summaries
        int summaryTokens = summaries.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();

        int remainingBudget = budgetForHistory - summaryTokens;
        if (remainingBudget <= 0) {
            // Even summaries are too long, keep only the last summary
            if (summaries.size() > 1) {
                summaries = summaries.subList(summaries.size() - 1, summaries.size());
                summaryTokens = summaries.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
                remainingBudget = budgetForHistory - summaryTokens;
                log.warn("Multiple summaries exceed budget, keeping only latest summary");
            }
            if (remainingBudget <= 0) {
                log.warn("Context too large even after trimming, sending minimal context");
                return summaries;
            }
        }

        // Keep as many recent non-summary messages as fit in budget
        List<Message> keptNonSummaries = new ArrayList<>();
        int usedTokens = 0;
        // Iterate from the end (most recent first)
        for (int i = nonSummaries.size() - 1; i >= 0; i--) {
            Message msg = nonSummaries.get(i);
            int msgTokens = estimateTokens(msg.getContent());
            if (usedTokens + msgTokens > remainingBudget) {
                break;
            }
            keptNonSummaries.add(0, msg); // prepend to maintain order
            usedTokens += msgTokens;
        }

        int dropped = nonSummaries.size() - keptNonSummaries.size();
        if (dropped > 0) {
            log.info("Dynamic context compression: kept {}/{} non-summary messages (dropped {} oldest), "
                            + "summary messages={}, total history tokens≈{}",
                    keptNonSummaries.size(), nonSummaries.size(), dropped,
                    summaries.size(), summaryTokens + usedTokens);
        }

        List<Message> result = new ArrayList<>(summaries);
        result.addAll(keptNonSummaries);
        return result;
    }

    /**
     * Estimate token count for a string.
     * CJK characters count as ~1 token each; ASCII ~4 chars per token.
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        int cjk = 0, ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA) {
                cjk++;
            } else {
                ascii++;
            }
        }
        return cjk + (ascii / 4) + 1;
    }
}
