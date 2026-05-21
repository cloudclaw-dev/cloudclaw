package run.cloudclaw.agent.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionResult;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A decorating ToolCallingManager that:
 * <ol>
 *   <li>Enforces a maximum number of tool call rounds per conversation turn</li>
 *   <li>Truncates tool result content exceeding a configurable max length</li>
 * </ol>
 */
@Slf4j
public class LimitedToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final int maxToolCalls;
    private final int maxToolResultChars;
    private int callCount = 0;

    public LimitedToolCallingManager(ToolCallingManager delegate, int maxToolCalls) {
        this(delegate, maxToolCalls, 3000);
    }

    public LimitedToolCallingManager(ToolCallingManager delegate, int maxToolCalls, int maxToolResultChars) {
        this.delegate = delegate;
        this.maxToolCalls = maxToolCalls;
        this.maxToolResultChars = maxToolResultChars;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        callCount++;
        if (callCount > maxToolCalls) {
            // Limit reached: return current response directly, no more tool execution
            List<Message> history =
                    new ArrayList<>(prompt.getInstructions());
            history.add(chatResponse.getResult().getOutput());
            return new DefaultToolExecutionResult(history, true);
        }
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        return result;
    }

    public int getCallCount() {
        return callCount;
    }

    public void reset() {
        callCount = 0;
    }
}
