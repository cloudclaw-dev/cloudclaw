package run.cloudclaw.agent.engine;

import run.cloudclaw.common.model.Message;
import run.cloudclaw.memory.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextCompressorTest {

    @Mock
    private TokenEstimator tokenEstimator;

    private ContextCompressor compressor;

    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        compressor = new ContextCompressor(tokenEstimator);
    }

    private Message createMessage(String role, String content) {
        return createMessage(role, content, false);
    }

    private Message createMessage(String role, String content, boolean isSummary) {
        Message msg = new Message();
        msg.setSessionId(SESSION_ID);
        msg.setRole(role);
        msg.setContent(content);
        msg.setIsSummary(isSummary);
        return msg;
    }

    /**
     * Helper: configure tokenEstimator mock to return fixed token counts.
     * Each pair is (substring, tokenCount). Calls are matched in order via anyString().
     */
    private void setupTokenEstimator(int... tokenCounts) {
        // Return values in sequence for any string
        for (int i = 0; i < tokenCounts.length; i++) {
            // We use thenReturn with multiple values
        }
        // Simpler approach: use answer that returns from an array
        final int[] counts = tokenCounts;
        final int[] idx = {0};
        when(tokenEstimator.estimateTokens(anyString())).thenAnswer(inv -> {
            if (idx[0] < counts.length) {
                return counts[idx[0]++];
            }
            return 0;
        });
    }

    @Nested
    @DisplayName("Empty and null history")
    class EmptyHistory {

        @Test
        @DisplayName("Should return empty list when messages is empty")
        void emptyMessages() {
            setupTokenEstimator(100, 50);
            List<Message> result = compressor.compress(
                    Collections.emptyList(), "system", "hello", 10000, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return original list when history fits within threshold")
        void withinThreshold() {
            // system=10, user=5 => threshold = 10000*0.75 - 10 - 5 = 7485
            // historyTokens = 3 messages * content estimation
            // Let's say each message content = 50 tokens => historyTokens = 150
            // 150 <= 7485 => no compression
            when(tokenEstimator.estimateTokens("system")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hello")).thenReturn(5);
            when(tokenEstimator.estimateTokens("msg1")).thenReturn(50);
            when(tokenEstimator.estimateTokens("msg2")).thenReturn(50);
            when(tokenEstimator.estimateTokens("msg3")).thenReturn(50);

            List<Message> messages = List.of(
                    createMessage("user", "msg1"),
                    createMessage("assistant", "msg2"),
                    createMessage("user", "msg3")
            );

            List<Message> result = compressor.compress(messages, "system", "hello", 10000, null);

            assertEquals(3, result.size());
            assertSame(messages, result); // should return the same list object
        }
    }

    @Nested
    @DisplayName("Compression logic")
    class CompressionLogic {

        @Test
        @DisplayName("Should truncate old non-summary messages when exceeding threshold")
        void shouldTruncateOldMessages() {
            // contextWindow=1000, system=10, user=5
            // threshold = 1000*0.75 - 10 - 5 = 735
            // budgetForHistory = 1000 - 10 - 5 - (1000-10-5)*0.25 = 985 - 246.25 ≈ 738
            //
            // 5 messages: msg1(200), msg2(200), msg3(200), msg4(200), msg5(200)
            // historyTokens = 1000 > threshold=735 => compression triggered
            // summaries = [], summaryTokens=0
            // remainingBudget = 738
            // Keep from end: msg5(200) + msg4(200) + msg3(200) = 600 <= 738
            // msg2(200) => 600+200=800 > 738 => stop
            // Result: msg3, msg4, msg5 (kept 3 of 5, dropped 2 oldest)

            when(tokenEstimator.estimateTokens("system")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hello")).thenReturn(5);
            // For history token calculation (5 messages)
            when(tokenEstimator.estimateTokens("msg1")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg2")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg3")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg4")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg5")).thenReturn(200);

            // Additional calls for summary/non-summary split + budget calc
            // The same content strings are used again in the filter loops
            // We need more returns for the second pass (summaryTokens + budget calc)
            // Summary pass: 0 summaries
            // Non-summary budget pass (from end): msg5(200), msg4(200), msg3(200), msg2(200)
            when(tokenEstimator.estimateTokens("msg5")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg4")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg3")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg2")).thenReturn(200);

            List<Message> messages = List.of(
                    createMessage("user", "msg1"),
                    createMessage("assistant", "msg2"),
                    createMessage("user", "msg3"),
                    createMessage("assistant", "msg4"),
                    createMessage("user", "msg5")
            );

            List<Message> result = compressor.compress(messages, "system", "hello", 1000, null);

            assertEquals(3, result.size());
            assertEquals("msg3", result.get(0).getContent());
            assertEquals("msg4", result.get(1).getContent());
            assertEquals("msg5", result.get(2).getContent());
        }

        @Test
        @DisplayName("Should keep summary messages and truncate old non-summary")
        void shouldKeepSummaries() {
            // contextWindow=1000, system=10, user=5
            // threshold=735, budgetForHistory≈738
            // summaries: [summaryMsg(300)] => summaryTokens=300
            // nonSummaries: [msg1(200), msg2(200), msg3(200)]
            // historyTokens = 300+600 = 900 > 735 => compress
            // remainingBudget = 738 - 300 = 438
            // Keep from end: msg3(200)+msg2(200)=400 <= 438, msg1(200)=>600>438
            // Result: summary + msg2 + msg3

            when(tokenEstimator.estimateTokens("system")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hello")).thenReturn(5);

            Message summaryMsg = createMessage("summary", "summary content");
            summaryMsg.setIsSummary(true);

            List<Message> messages = List.of(
                    summaryMsg,
                    createMessage("user", "msg1"),
                    createMessage("assistant", "msg2"),
                    createMessage("user", "msg3")
            );

            // First pass: historyTokens calculation
            when(tokenEstimator.estimateTokens("summary content")).thenReturn(300);
            when(tokenEstimator.estimateTokens("msg1")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg2")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg3")).thenReturn(200);

            // Second pass: summaryTokens
            when(tokenEstimator.estimateTokens("summary content")).thenReturn(300);
            // Non-summary budget: from end
            when(tokenEstimator.estimateTokens("msg3")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg2")).thenReturn(200);
            when(tokenEstimator.estimateTokens("msg1")).thenReturn(200);

            List<Message> result = compressor.compress(messages, "system", "hello", 1000, null);

            assertEquals(3, result.size());
            assertEquals("summary content", result.get(0).getContent());
            assertTrue(result.get(0).getIsSummary());
        }

        @Test
        @DisplayName("Summary messages with role 'summary' (not just isSummary flag) are preserved")
        void summaryRolePreserved() {
            when(tokenEstimator.estimateTokens("system")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hello")).thenReturn(5);
            when(tokenEstimator.estimateTokens("past summary")).thenReturn(100);

            // Single summary-role message, small context so no compression needed
            // historyTokens=100, threshold=735 => no compression
            List<Message> messages = List.of(
                    createMessage("summary", "past summary")
            );

            List<Message> result = compressor.compress(messages, "system", "hello", 1000, null);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Boundary cases")
    class BoundaryCases {

        @Test
        @DisplayName("Should return messages as-is when threshold <= 0")
        void thresholdZeroOrNegative() {
            // system+user tokens > contextWindow*0.75 => threshold <= 0
            when(tokenEstimator.estimateTokens("very long system prompt")).thenReturn(8000);
            when(tokenEstimator.estimateTokens("hello")).thenReturn(1000);

            List<Message> messages = List.of(
                    createMessage("user", "msg1"),
                    createMessage("assistant", "msg2")
            );

            List<Message> result = compressor.compress(
                    messages, "very long system prompt", "hello", 10000, null);

            assertSame(messages, result); // returns original unchanged
        }

        @Test
        @DisplayName("Should return only summaries when even summaries exceed budget")
        void summariesExceedBudget() {
            // contextWindow=200, system=10, user=5
            // threshold = 200*0.75 - 10 - 5 = 135
            // budgetForHistory = 200 - 10 - 5 - (200-10-5)*0.25 = 185 - 46 = 139
            // Two summaries each 100 tokens => summaryTokens=200 > budgetForHistory(139)
            // => Keep only last summary
            // remainingBudget = 139 - 100 = 39 > 0 => return [lastSummary]

            when(tokenEstimator.estimateTokens("sys")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hi")).thenReturn(5);
            when(tokenEstimator.estimateTokens("summary1")).thenReturn(100);
            when(tokenEstimator.estimateTokens("summary2")).thenReturn(100);

            // Second pass for summaries
            when(tokenEstimator.estimateTokens("summary1")).thenReturn(100);
            when(tokenEstimator.estimateTokens("summary2")).thenReturn(100);

            List<Message> messages = List.of(
                    createMessage("summary", "summary1"),
                    createMessage("summary", "summary2")
            );

            List<Message> result = compressor.compress(messages, "sys", "hi", 200, null);

            assertEquals(1, result.size());
            assertEquals("summary2", result.get(0).getContent());
        }

        @Test
        @DisplayName("Should use custom usageThreshold when provided")
        void customUsageThreshold() {
            // usageThreshold=0.5 => threshold = 1000*0.5 - 10 - 5 = 485
            // 3 messages each 200 tokens => historyTokens=600 > 485 => compress
            // budgetForHistory = 1000 - 10 - 5 - (1000-10-5)*0.25 ≈ 738
            // summaries=0, remainingBudget=738
            // Keep from end: msg3(200)+msg2(200)+msg1(200)=600<=738 => keep all 3

            when(tokenEstimator.estimateTokens("sys")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hi")).thenReturn(5);
            when(tokenEstimator.estimateTokens("a")).thenReturn(200);
            when(tokenEstimator.estimateTokens("b")).thenReturn(200);
            when(tokenEstimator.estimateTokens("c")).thenReturn(200);
            // Budget pass
            when(tokenEstimator.estimateTokens("c")).thenReturn(200);
            when(tokenEstimator.estimateTokens("b")).thenReturn(200);
            when(tokenEstimator.estimateTokens("a")).thenReturn(200);

            List<Message> messages = List.of(
                    createMessage("user", "a"),
                    createMessage("assistant", "b"),
                    createMessage("user", "c")
            );

            List<Message> result = compressor.compress(messages, "sys", "hi", 1000, 0.5);

            // With lower threshold, compression triggers but budget allows keeping all
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should handle null content messages gracefully")
        void nullContentMessages() {
            when(tokenEstimator.estimateTokens("sys")).thenReturn(10);
            when(tokenEstimator.estimateTokens("hi")).thenReturn(5);
            when(tokenEstimator.estimateTokens((String) null)).thenReturn(0);
            when(tokenEstimator.estimateTokens("msg")).thenReturn(50);

            List<Message> messages = new ArrayList<>();
            Message nullMsg = new Message();
            nullMsg.setSessionId(SESSION_ID);
            nullMsg.setRole("user");
            nullMsg.setContent(null);
            messages.add(nullMsg);
            messages.add(createMessage("user", "msg"));

            List<Message> result = compressor.compress(messages, "sys", "hi", 10000, null);

            // null content => 0 tokens, should be included in result
            assertEquals(2, result.size());
        }
    }
}
