package run.cloudclaw.agent.engine;

import run.cloudclaw.common.model.Message;
import run.cloudclaw.llm.service.LlmRouteService;
import run.cloudclaw.session.repository.MessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compresses old session messages into a concise summary to keep context
 * within model token limits.
 *
 * <p>When a session exceeds {@code maxRounds} conversation rounds,
 * the oldest messages are summarized by the LLM into a single summary message
 * (role=summary). The original messages are marked as compressed.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionCompressor {

    private final MessageRepository messageRepository;
    private final LlmRouteService llmRouteService;

    /** Default max conversation rounds before triggering compression */
    private static final int DEFAULT_MAX_ROUNDS = 20;
    /** Number of recent rounds to keep uncompressed */
    private static final int DEFAULT_KEEP_RECENT_ROUNDS = 6;

    private static final String SUMMARY_PROMPT = """
            You are a conversation summarizer. Summarize the following conversation history into a concise summary.
            Preserve:
            - Key facts, decisions, and conclusions
            - Important user preferences or requirements mentioned
            - Any ongoing tasks or open questions
            - Technical details (code snippets, configurations, error messages) that may be needed later
            
            Write in the same language as the conversation. Be concise but comprehensive.
            Output ONLY the summary, no preamble.
            
            Conversation history:
            """;

    /**
     * Check if compression is needed and execute if so.
     *
     * @param sessionId the session to check
     * @param modelId   the LLM model to use for summarization
     */
    public void compressIfNeeded(String sessionId, String modelId) {
        compressIfNeeded(sessionId, modelId, null, null);
    }

    /**
     * Check if compression is needed and execute if so.
     *
     * @param sessionId         the session to check
     * @param modelId           the LLM model to use for summarization
     * @param maxRounds         max rounds before triggering compression (null = default)
     * @param keepRecentRounds  recent rounds to keep uncompressed (null = default)
     */
    @Transactional
    public void compressIfNeeded(String sessionId, String modelId, Integer maxRounds, Integer keepRecentRounds) {
        int threshold = maxRounds != null ? maxRounds : DEFAULT_MAX_ROUNDS;
        int keepRounds = keepRecentRounds != null ? keepRecentRounds : DEFAULT_KEEP_RECENT_ROUNDS;
        UUID sessionUuid = UUID.fromString(sessionId);

        // Count non-compressed, non-summary messages
        List<Message> activeMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid)
                .stream()
                .filter(m -> !Boolean.TRUE.equals(m.getCompressed()) && !Boolean.TRUE.equals(m.getIsSummary()))
                .toList();

        // Each round = user + assistant pair
        long userCount = activeMessages.stream().filter(m -> "user".equals(m.getRole())).count();
        long assistantCount = activeMessages.stream().filter(m -> "assistant".equals(m.getRole())).count();
        long rounds = Math.min(userCount, assistantCount);

        if (rounds <= threshold) {
            log.debug("Session {} has {} rounds, no compression needed (threshold={})", sessionId, rounds, threshold);
            return;
        }

        log.info("Session {} has {} rounds, exceeds threshold {}. Starting compression...",
                sessionId, rounds, threshold);

        // Messages to compress: all except the most recent keepRounds rounds
        // Each round is 2 messages (user + assistant), so keep last keepRounds*2
        int keepCount = keepRounds * 2;
        int compressCount = activeMessages.size() - keepCount;

        if (compressCount <= 2) {
            log.debug("Not enough messages to compress after keeping recent rounds");
            return;
        }

        List<Message> toCompress = activeMessages.subList(0, compressCount);

        // Build conversation text for summarization
        String conversationText = toCompress.stream()
                .map(m -> "[" + m.getRole() + "]: " + m.getContent())
                .collect(Collectors.joining("\n\n"));

        // Truncate if too long (prevent summarization call from being huge)
        int maxChars = 30000;
        if (conversationText.length() > maxChars) {
            conversationText = conversationText.substring(conversationText.length() - maxChars);
        }

        // Call LLM to generate summary
        String summary;
        try {
            ChatClient chatClient = llmRouteService.getChatClient(modelId);
            summary = chatClient.prompt()
                    .system(SUMMARY_PROMPT)
                    .user(conversationText)
                    .call()
                    .content();

            if (summary == null || summary.isBlank()) {
                log.warn("LLM returned empty summary for session {}, skipping compression", sessionId);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to generate summary for session {}: {}", sessionId, e.getMessage());
            return;
        }

        // Mark old messages as compressed
        for (Message msg : toCompress) {
            msg.setCompressed(true);
            messageRepository.save(msg);
        }

        // Check if there's already a summary message and append to it, or create new
        List<Message> existingSummaries = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid)
                .stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsSummary()))
                .toList();

        if (!existingSummaries.isEmpty()) {
            // Append to existing summary
            Message latestSummary = existingSummaries.get(existingSummaries.size() - 1);
            latestSummary.setContent(latestSummary.getContent() + "\n\n---\n\n" + summary);
            messageRepository.save(latestSummary);
            log.info("Appended to existing summary for session {} (total {} compressed messages)",
                    sessionId, toCompress.size());
        } else {
            // Create new summary message
            Message summaryMsg = new Message();
            summaryMsg.setSessionId(sessionUuid);
            summaryMsg.setRole("summary");
            summaryMsg.setContent("[Conversation Summary]\n\n" + summary);
            summaryMsg.setIsSummary(true);
            summaryMsg.setCompressed(false);
            summaryMsg.setCreatedAt(toCompress.get(toCompress.size() - 1).getCreatedAt());
            messageRepository.save(summaryMsg);
            log.info("Created summary for session {} (compressed {} messages)",
                    sessionId, toCompress.size());
        }
    }

    /**
     * Load session context with compression-aware logic.
     * Returns: summary messages + recent non-compressed messages.
     */
    @Transactional(readOnly = true)
    public List<Message> loadContextWithSummary(String sessionId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        List<Message> allMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);

        // Filter: include summary messages + non-compressed messages
        return allMessages.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsSummary())
                        || !Boolean.TRUE.equals(m.getCompressed()))
                .toList();
    }
}
