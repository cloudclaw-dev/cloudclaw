package run.cloudclaw.agent.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TruncatingToolCallbackTest {

    @Mock private ToolCallback delegate;
    @Mock private ToolDefinition toolDefinition;

    private static final String TOOL_NAME = "test_tool";

    @Nested
    @DisplayName("call(String) - basic truncation")
    class BasicTruncation {

        @Test
        @DisplayName("Should not truncate content within limit")
        void withinLimit() {
            when(delegate.call("input")).thenReturn("short result");

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input");

            assertEquals("short result", result);
        }

        @Test
        @DisplayName("Should truncate content exceeding maxResultChars")
        void exceedsLimit() {
            String longResult = "A".repeat(200);
            when(delegate.call("input")).thenReturn(longResult);
            when(delegate.getToolDefinition()).thenReturn(toolDefinition);
            when(toolDefinition.name()).thenReturn(TOOL_NAME);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 50);
            String result = callback.call("input");

            assertTrue(result.length() > 50); // includes truncation notice
            assertTrue(result.startsWith("A".repeat(50)));
            assertTrue(result.contains("[Truncated:"));
            assertTrue(result.contains("150 characters omitted"));
            assertTrue(result.contains("Original: 200 chars"));
        }

        @Test
        @DisplayName("Should handle null result")
        void nullResult() {
            when(delegate.call("input")).thenReturn(null);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input");

            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty result")
        void emptyResult() {
            when(delegate.call("input")).thenReturn("");

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input");

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should not truncate when content length equals maxResultChars")
        void exactLimit() {
            String exactResult = "A".repeat(50);
            when(delegate.call("input")).thenReturn(exactResult);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 50);
            String result = callback.call("input");

            assertEquals(exactResult, result);
            assertFalse(result.contains("[Truncated"));
        }
    }

    @Nested
    @DisplayName("call(String, ToolContext)")
    class ToolContextTruncation {

        @Test
        @DisplayName("Should truncate in ToolContext overload as well")
        void truncationWithContext() {
            String longResult = "B".repeat(300);
            when(delegate.call("input", null)).thenReturn(longResult);
            when(delegate.getToolDefinition()).thenReturn(toolDefinition);
            when(toolDefinition.name()).thenReturn(TOOL_NAME);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input", null);

            assertTrue(result.startsWith("B".repeat(100)));
            assertTrue(result.contains("[Truncated:"));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should catch delegate exception and return error message")
        void delegateException() {
            when(delegate.call("input")).thenThrow(new RuntimeException("Tool execution failed"));
            when(delegate.getToolDefinition()).thenReturn(toolDefinition);
            when(toolDefinition.name()).thenReturn(TOOL_NAME);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input");

            assertTrue(result.startsWith("Error:"));
            assertTrue(result.contains("Tool execution failed"));
        }

        @Test
        @DisplayName("Should catch delegate exception in ToolContext overload")
        void delegateExceptionWithContext() {
            when(delegate.call("input", null)).thenThrow(new RuntimeException("Failed"));
            when(delegate.getToolDefinition()).thenReturn(toolDefinition);
            when(toolDefinition.name()).thenReturn(TOOL_NAME);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);
            String result = callback.call("input", null);

            assertTrue(result.startsWith("Error:"));
        }
    }

    @Nested
    @DisplayName("Delegation")
    class Delegation {

        @Test
        @DisplayName("Should delegate getToolDefinition to inner callback")
        void delegateToolDefinition() {
            when(delegate.getToolDefinition()).thenReturn(toolDefinition);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);

            assertEquals(toolDefinition, callback.getToolDefinition());
        }

        @Test
        @DisplayName("Should delegate getToolMetadata to inner callback")
        void delegateToolMetadata() {
            when(delegate.getToolMetadata()).thenReturn(null);

            TruncatingToolCallback callback = new TruncatingToolCallback(delegate, 100);

            assertNull(callback.getToolMetadata());
        }
    }
}
