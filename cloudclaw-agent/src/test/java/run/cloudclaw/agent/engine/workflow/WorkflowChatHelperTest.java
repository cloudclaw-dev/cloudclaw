package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WorkflowChatHelperTest {

    // We only test toAiMessages() which is a pure function with no external dependencies.
    // WorkflowChatHelper requires many constructor args, so we use reflection-free direct testing.

    private TestableWorkflowChatHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TestableWorkflowChatHelper();
    }

    /**
     * Minimal subclass to expose toAiMessages() for testing without needing
     * to mock all constructor dependencies.
     */
    static class TestableWorkflowChatHelper extends WorkflowChatHelper {
        TestableWorkflowChatHelper() {
            // Pass nulls — toAiMessages() doesn't use any field
            super(null, null, null, null, null);
        }
    }

    private Message createMessage(String role, String content) {
        Message msg = new Message();
        msg.setSessionId(UUID.randomUUID());
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }

    @Nested
    @DisplayName("toAiMessages - null and empty history")
    class NullEmptyHistory {

        @Test
        @DisplayName("Should return empty list for null history")
        void nullHistory() {
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for empty history")
        void emptyHistory() {
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(Collections.emptyList());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("toAiMessages - role conversion")
    class RoleConversion {

        @Test
        @DisplayName("Should convert user role to UserMessage")
        void userRole() {
            List<Message> history = List.of(createMessage("user", "Hello"));
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertEquals("Hello", result.get(0).getText());
        }

        @Test
        @DisplayName("Should convert assistant role to AssistantMessage")
        void assistantRole() {
            List<Message> history = List.of(createMessage("assistant", "Hi there"));
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertInstanceOf(AssistantMessage.class, result.get(0));
            assertEquals("Hi there", result.get(0).getText());
        }

        @Test
        @DisplayName("Should convert system role to SystemMessage")
        void systemRole() {
            List<Message> history = List.of(createMessage("system", "System info"));
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertInstanceOf(SystemMessage.class, result.get(0));
            assertEquals("System info", result.get(0).getText());
        }

        @Test
        @DisplayName("Should convert summary role to SystemMessage with prefix")
        void summaryRole() {
            List<Message> history = List.of(createMessage("summary", "Past conversation"));
            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertInstanceOf(SystemMessage.class, result.get(0));
            assertTrue(result.get(0).getText().startsWith("[Conversation Summary]\n"));
            assertTrue(result.get(0).getText().contains("Past conversation"));
        }

        @Test
        @DisplayName("Should convert multiple messages preserving order")
        void multipleMessages() {
            List<Message> history = List.of(
                    createMessage("user", "msg1"),
                    createMessage("assistant", "msg2"),
                    createMessage("user", "msg3")
            );

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(3, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            assertInstanceOf(UserMessage.class, result.get(2));
        }
    }

    @Nested
    @DisplayName("toAiMessages - filtering")
    class Filtering {

        @Test
        @DisplayName("Should skip tool_call messages")
        void skipToolCall() {
            List<Message> history = List.of(
                    createMessage("user", "Hello"),
                    createMessage("tool_call", "{\"name\":\"search\"}"),
                    createMessage("assistant", "Result")
            );

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(2, result.size());
            assertEquals("Hello", result.get(0).getText());
            assertEquals("Result", result.get(1).getText());
        }

        @Test
        @DisplayName("Should skip messages with null content")
        void skipNullContent() {
            Message nullMsg = new Message();
            nullMsg.setSessionId(UUID.randomUUID());
            nullMsg.setRole("user");
            nullMsg.setContent(null);

            List<Message> history = List.of(nullMsg, createMessage("assistant", "response"));

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertEquals("response", result.get(0).getText());
        }

        @Test
        @DisplayName("Should skip messages with blank content")
        void skipBlankContent() {
            List<Message> history = List.of(
                    createMessage("user", "   "),
                    createMessage("assistant", ""),
                    createMessage("user", "valid")
            );

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(1, result.size());
            assertEquals("valid", result.get(0).getText());
        }

        @Test
        @DisplayName("Should skip unknown roles")
        void skipUnknownRoles() {
            List<Message> history = List.of(
                    createMessage("user", "Hello"),
                    createMessage("tool_result", "some result"),
                    createMessage("assistant", "Reply")
            );

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            // "tool_result" is an unknown role => skipped
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should handle all filtering combinations together")
        void combinedFiltering() {
            List<Message> history = new ArrayList<>();
            history.add(createMessage("user", "Hello"));
            history.add(createMessage("tool_call", "ignored"));
            history.add(createMessage("user", "   ")); // blank
            Message nullContent = new Message();
            nullContent.setSessionId(UUID.randomUUID());
            nullContent.setRole("assistant");
            nullContent.setContent(null);
            history.add(nullContent);
            history.add(createMessage("assistant", "Final reply"));

            List<org.springframework.ai.chat.messages.Message> result = helper.toAiMessages(history);

            assertEquals(2, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
        }
    }
}
