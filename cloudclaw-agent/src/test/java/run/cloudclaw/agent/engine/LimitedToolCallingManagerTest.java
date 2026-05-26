package run.cloudclaw.agent.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LimitedToolCallingManagerTest {

    @Mock private ToolCallingManager delegate;

    private LimitedToolCallingManager manager;

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should use default maxToolResultChars (3000) when not specified")
        void defaultMaxToolResultChars() {
            manager = new LimitedToolCallingManager(delegate, 10);
            assertNotNull(manager);
        }

        @Test
        @DisplayName("Should accept custom maxToolResultChars")
        void customMaxToolResultChars() {
            manager = new LimitedToolCallingManager(delegate, 10, 5000);
            assertNotNull(manager);
        }
    }

    @Nested
    @DisplayName("Call count and limit")
    class CallCountLimit {

        @BeforeEach
        void setUp() {
            manager = new LimitedToolCallingManager(delegate, 3);
        }

        @Test
        @DisplayName("Should start with call count 0")
        void initialCount() {
            assertEquals(0, manager.getCallCount());
        }

        @Test
        @DisplayName("Should delegate executeToolCalls when under limit")
        void underLimit() {
            Prompt prompt = new Prompt(List.of(new SystemMessage("test")));
            ChatResponse chatResponse = createChatResponse("call tools");
            ToolExecutionResult expectedResult = new DefaultToolExecutionResult(List.of(), false);

            when(delegate.executeToolCalls(prompt, chatResponse)).thenReturn(expectedResult);

            ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);

            assertEquals(1, manager.getCallCount());
            assertSame(expectedResult, result);
            verify(delegate).executeToolCalls(prompt, chatResponse);
        }

        @Test
        @DisplayName("Should allow exactly maxToolCalls calls")
        void exactlyAtLimit() {
            Prompt prompt = new Prompt(List.of(new SystemMessage("test")));
            ChatResponse chatResponse = createChatResponse("call");

            when(delegate.executeToolCalls(any(), any()))
                    .thenReturn(new DefaultToolExecutionResult(List.of(), false));

            for (int i = 0; i < 3; i++) {
                manager.executeToolCalls(prompt, chatResponse);
            }

            assertEquals(3, manager.getCallCount());
            verify(delegate, times(3)).executeToolCalls(any(), any());
        }

        @Test
        @DisplayName("Should stop delegating when exceeding limit")
        void overLimit() {
            Prompt prompt = new Prompt(List.of(new SystemMessage("test")));
            ChatResponse chatResponse = createChatResponse("call");

            when(delegate.executeToolCalls(any(), any()))
                    .thenReturn(new DefaultToolExecutionResult(List.of(), false));

            // Call 3 times (at limit)
            for (int i = 0; i < 3; i++) {
                manager.executeToolCalls(prompt, chatResponse);
            }

            // 4th call exceeds limit
            ToolExecutionResult result = manager.executeToolCalls(prompt, chatResponse);

            assertEquals(4, manager.getCallCount());
            // Should return without delegating
            assertTrue(result.conversationHistory().size() > 0);
            assertTrue(result.returnDirect());
            // Delegate should only have been called 3 times
            verify(delegate, times(3)).executeToolCalls(any(), any());
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("Should reset call count to 0")
        void resetCount() {
            manager = new LimitedToolCallingManager(delegate, 5);
            Prompt prompt = new Prompt(List.of(new SystemMessage("test")));
            ChatResponse chatResponse = createChatResponse("call");

            when(delegate.executeToolCalls(any(), any()))
                    .thenReturn(new DefaultToolExecutionResult(List.of(), false));

            manager.executeToolCalls(prompt, chatResponse);
            assertEquals(1, manager.getCallCount());

            manager.reset();
            assertEquals(0, manager.getCallCount());

            // Should delegate again after reset
            manager.executeToolCalls(prompt, chatResponse);
            assertEquals(1, manager.getCallCount());
        }
    }

    @Nested
    @DisplayName("resolveToolDefinitions")
    class ResolveToolDefinitions {

        @Test
        @DisplayName("Should delegate resolveToolDefinitions")
        void delegateResolve() {
            manager = new LimitedToolCallingManager(delegate, 5);
            when(delegate.resolveToolDefinitions(any())).thenReturn(List.of());

            var options = mock(org.springframework.ai.model.tool.ToolCallingChatOptions.class);
            manager.resolveToolDefinitions(options);

            verify(delegate).resolveToolDefinitions(options);
        }
    }

    private ChatResponse createChatResponse(String content) {
        AssistantMessage assistantMessage = new AssistantMessage(content);
        Generation generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }
}
