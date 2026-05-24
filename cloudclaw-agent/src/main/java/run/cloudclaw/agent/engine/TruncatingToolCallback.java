package run.cloudclaw.agent.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * A decorating ToolCallback that truncates tool result content exceeding a
 * configurable max length. This prevents oversized tool outputs from
 * consuming the entire context window.
 *
 * <p>Wraps any ToolCallback (skill tools, MCP tools, etc.) transparently.</p>
 */
@Slf4j
public class TruncatingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final int maxResultChars;

    public TruncatingToolCallback(ToolCallback delegate, int maxResultChars) {
        this.delegate = delegate;
        this.maxResultChars = maxResultChars;
    }

    @Override
    public String call(String toolInput) {
        try {
            String result = delegate.call(toolInput);
            return truncate(result);
        } catch (Exception e) {
            log.error("Tool call failed: {}", getToolDefinition().name(), e);
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        try {
            String result = delegate.call(toolInput, toolContext);
            return truncate(result);
        } catch (Exception e) {
            log.error("Tool call failed: {}", getToolDefinition().name(), e);
            return "Error: " + e.getMessage();
        }
    }

    private String truncate(String content) {
        if (content == null || content.length() <= maxResultChars) {
            return content;
        }
        log.info("Tool result truncated: {} chars -> {} chars (tool={})",
                content.length(), maxResultChars, getToolDefinition().name());
        return content.substring(0, maxResultChars)
                + "\n\n[Truncated: " + (content.length() - maxResultChars)
                + " characters omitted. Original: " + content.length() + " chars]";
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }
}
