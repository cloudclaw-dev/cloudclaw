package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.agent.engine.ToolResolutionService;
import run.cloudclaw.agent.prompt.PromptAssembler;
import run.cloudclaw.agent.prompt.PromptLogService;
import run.cloudclaw.memory.service.TokenEstimator;
import run.cloudclaw.llm.service.LlmRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Helper for invoking LLM calls within workflow executors.
 * Extracts the common pattern of building ChatClient requests with tools.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowChatHelper {

    private final LlmRouteService llmRouteService;
    private final ToolResolutionService toolResolutionService;
    private final PromptAssembler promptAssembler;
    private final PromptLogService promptLogService;
    private final TokenEstimator tokenEstimator;

    /**
     * Log context for workflow node prompt logging.
     */
    public static class LogContext {
        public final String sessionId;
        public final String agentId;
        public final String userId;
        public final String nodeName;

        public LogContext(String sessionId, String agentId, String userId, String nodeName) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.userId = userId;
            this.nodeName = nodeName;
        }
    }

    private void logPrompt(LogContext ctx, String modelId, String role, String content) {
        if (ctx != null && promptLogService != null && content != null && !content.isBlank()) {
            promptLogService.logAsync(ctx.sessionId, ctx.agentId + "/" + ctx.nodeName,
                    ctx.userId, modelId, role, content, estimateTokens(content), null, null);
        }
    }

    /**
     * Simple LLM call: system prompt + user message, returns the text response.
     */
    public String callLlm(String modelId, String systemPrompt, String userMessage) {
        return callLlm(modelId, systemPrompt, userMessage, null);
    }

    public String callLlm(String modelId, String systemPrompt, String userMessage, LogContext logCtx) {
        logPrompt(logCtx, modelId, "system", systemPrompt);
        logPrompt(logCtx, modelId, "user", userMessage);
        ChatClient chatClient = llmRouteService.getChatClient(modelId);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * LLM call with tools: system prompt + user message + tool callbacks.
     * Returns the text response. Tool calls are handled automatically by ToolCallAdvisor.
     */
    public String callLlmWithTools(String modelId, String systemPrompt, String userMessage,
                                    List<ToolCallback> toolCallbacks, int maxToolCalls) {
        return callLlmWithTools(modelId, systemPrompt, userMessage, toolCallbacks, maxToolCalls, null);
    }

    public String callLlmWithTools(String modelId, String systemPrompt, String userMessage,
                                    List<ToolCallback> toolCallbacks, int maxToolCalls, LogContext logCtx) {
        logPrompt(logCtx, modelId, "system", systemPrompt);
        logPrompt(logCtx, modelId, "user", userMessage);
        ChatClient chatClient = llmRouteService.getChatClient(modelId);
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage);

        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            ToolCallingManager toolCallingManager = new run.cloudclaw.agent.engine.LimitedToolCallingManager(
                    DefaultToolCallingManager.builder().build(),
                    maxToolCalls
            );
            ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .build();
            spec.toolCallbacks(toolCallbacks).advisors(toolCallAdvisor);
        }

        return spec.call().content();
    }

    /**
     * Simple streaming LLM call: returns a Flux of text content chunks.
     */
    public reactor.core.publisher.Flux<String> streamLlm(String modelId, String systemPrompt, String userMessage) {
        return streamLlm(modelId, systemPrompt, userMessage, null);
    }

    public reactor.core.publisher.Flux<String> streamLlm(String modelId, String systemPrompt, String userMessage, LogContext logCtx) {
        logPrompt(logCtx, modelId, "system", systemPrompt);
        logPrompt(logCtx, modelId, "user", userMessage);
        ChatClient chatClient = llmRouteService.getChatClient(modelId);
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content();
    }

    /**
     * Streaming LLM call with tool callbacks support.
     */
    public reactor.core.publisher.Flux<String> streamLlmWithTools(
            String modelId, String systemPrompt, String userMessage,
            List<ToolCallback> toolCallbacks, int maxToolCalls) {
        return streamLlmWithTools(modelId, systemPrompt, userMessage, toolCallbacks, maxToolCalls, null);
    }

    public reactor.core.publisher.Flux<String> streamLlmWithTools(
            String modelId, String systemPrompt, String userMessage,
            List<ToolCallback> toolCallbacks, int maxToolCalls, LogContext logCtx) {
        logPrompt(logCtx, modelId, "system", systemPrompt);
        logPrompt(logCtx, modelId, "user", userMessage);

        ChatClient chatClient = llmRouteService.getChatClient(modelId);
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage);

        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            ToolCallingManager toolCallingManager = new run.cloudclaw.agent.engine.LimitedToolCallingManager(
                    DefaultToolCallingManager.builder().build(),
                    maxToolCalls
            );
            ToolCallAdvisor toolCallAdvisor = ToolCallAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .streamToolCallResponses(false)
                    .build();
            spec.toolCallbacks(toolCallbacks).advisors(toolCallAdvisor);
        }

        return spec.stream().content();
    }

    /**
     * Resolve tool callbacks for a given config. Returns callbacks and populates createdMcpClients.
     * Delegates to {@link ToolResolutionService}.
     */
    public List<ToolCallback> resolveToolCallbacks(AgentConfig config, List<McpSyncClient> createdMcpClients) {
        return toolResolutionService.resolveToolCallbacks(config, createdMcpClients);
    }

    /**
     * Build an AgentConfig for a resolved node agent, inheriting from parent.
     * Delegates to {@link ToolResolutionService#buildSubAgentConfig}.
     */
    public AgentConfig buildNodeConfig(AgentConfig parent, ResolvedNodeAgent node) {
        ResolvedAgent resolvedAgent = new ResolvedAgent(
                node.getName(),
                node.getDisplayName(),
                node.getSystemPrompt(),
                node.getModelId(),
                node.getMcpServerIds(),
                node.getSkillIds(),
                false
        );
        return toolResolutionService.buildSubAgentConfig(parent, resolvedAgent);
    }

    /**
     * Build system prompt for a node: base prompt + date/time.
     * Uses PromptAssembler for proper prompt construction.
     */
    public String buildNodeSystemPrompt(ResolvedNodeAgent node, AgentConfig parentConfig,
                                         String userId, String sessionId, String userMessage) {
        // Create a ResolvedAgent from the node for PromptAssembler compatibility
        ResolvedAgent resolvedAgent = new ResolvedAgent(
                node.getName(),
                node.getDisplayName(),
                node.getSystemPrompt(),
                node.getModelId(),
                node.getMcpServerIds(),
                node.getSkillIds(),
                false
        );
        return promptAssembler.assembleSubAgentSystemPrompt(resolvedAgent, parentConfig, userId, sessionId, userMessage);
    }

    /**
     * Build a done ChatChunk with context statistics.
     */
    public ChatChunk buildDoneChunk(AgentConfig config, String userMessage, String response) {
        int userTokens = estimateTokens(userMessage);
        int respTokens = estimateTokens(response);
        int totalTokens = userTokens + respTokens;
        int maxContextTokens = config.getContextWindow() != null ? config.getContextWindow() : 128000;
        int usagePercent = (int) ((long) totalTokens * 100 / maxContextTokens);

        ChatChunk.ContextStats stats = ChatChunk.ContextStats.builder()
                .totalTokens(totalTokens)
                .historyMessages(2)
                .toolCallCount(0)
                .maxTokens(maxContextTokens)
                .usagePercent(Math.min(usagePercent, 100))
                .systemTokens(0)
                .historyTokens(0)
                .memoryTokens(0)
                .userMessageTokens(userTokens)
                .toolResultTokens(0)
                .build();

        return ChatChunk.builder()
                .content("")
                .toolCall(false)
                .done(true)
                .contextStats(stats)
                .build();
    }

    protected int estimateTokens(String text) {
        return tokenEstimator.estimateTokens(text);
    }
}
