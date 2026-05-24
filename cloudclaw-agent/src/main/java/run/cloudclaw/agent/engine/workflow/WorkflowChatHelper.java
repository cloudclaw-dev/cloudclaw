package run.cloudclaw.agent.engine.workflow;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.model.McpServer;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.agent.tools.SkillTools;
import run.cloudclaw.agent.tools.MemoryTools;
import run.cloudclaw.agent.prompt.PromptAssembler;
import run.cloudclaw.agent.prompt.PromptLogService;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.memory.service.TokenEstimator;
import run.cloudclaw.llm.service.LlmRouteService;
import run.cloudclaw.mcp.repository.McpServerRepository;
import run.cloudclaw.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final McpServerRepository mcpServerRepository;
    private final SkillService skillService;
    private final SkillTools skillTools;
    private final MemoryTools memoryTools;
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
                    .streamToolCallResponses(true)
                    .build();
            spec.toolCallbacks(toolCallbacks).advisors(toolCallAdvisor);
        }

        return spec.stream().content();
    }

    /**
     * Resolve tool callbacks for a given config. Returns callbacks and populates createdMcpClients.
     */
    public List<ToolCallback> resolveToolCallbacks(AgentConfig config, List<McpSyncClient> createdMcpClients) {
        List<ToolCallback> callbacks = new ArrayList<>();

        // Memory tools
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            try {
                MethodToolCallbackProvider memoryProvider = MethodToolCallbackProvider.builder()
                        .toolObjects(memoryTools)
                        .build();
                callbacks.addAll(Arrays.asList(memoryProvider.getToolCallbacks()));
            } catch (Exception e) {
                log.warn("Failed to resolve memory tools: {}", e.getMessage());
            }
        }

        // Skill tools
        try {
            List<Skill> agentSkills = skillService.getSkillsForAgent(config.getAgentId());
            if (!agentSkills.isEmpty()) {
                MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                        .toolObjects(skillTools)
                        .build();
                callbacks.addAll(Arrays.asList(provider.getToolCallbacks()));
            }
        } catch (Exception e) {
            log.warn("Failed to resolve skill tools: {}", e.getMessage());
        }

        // MCP Server tools
        try {
            List<String> mcpServerIds = config.getMcpServerIds();
            if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
                List<McpSyncClient> mcpClients = new ArrayList<>();
                for (String serverId : mcpServerIds) {
                    try {
                        McpServer server = mcpServerRepository.findById(UUID.fromString(serverId)).orElse(null);
                        if (server == null || !Boolean.TRUE.equals(server.getEnabled())) continue;
                        McpSyncClient mcpClient = createMcpClient(server);
                        if (mcpClient != null) mcpClients.add(mcpClient);
                    } catch (Exception e) {
                        log.warn("Failed to connect to MCP Server {}: {}", serverId, e.getMessage());
                    }
                }
                if (!mcpClients.isEmpty()) {
                    SyncMcpToolCallbackProvider mcpProvider = SyncMcpToolCallbackProvider.builder()
                            .mcpClients(mcpClients).build();
                    callbacks.addAll(Arrays.asList(mcpProvider.getToolCallbacks()));
                }
                createdMcpClients.addAll(mcpClients);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve MCP tools: {}", e.getMessage());
        }

        return callbacks;
    }

    /**
     * Build an AgentConfig for a resolved node agent, inheriting from parent.
     */
    public AgentConfig buildNodeConfig(AgentConfig parent, ResolvedNodeAgent node) {
        AgentConfig c = new AgentConfig();
        c.setAgentId(parent.getAgentId());
        c.setName(node.getDisplayName());
        c.setSystemPrompt(node.getSystemPrompt());
        c.setModelId(node.getModelId());
        c.setTemperature(parent.getTemperature());
        c.setMaxTokens(parent.getMaxTokens());
        c.setMaxToolCalls(parent.getMaxToolCalls());
        c.setEnabled(parent.getEnabled());
        c.setMcpServerIds(node.getMcpServerIds());
        c.setSkillIds(node.getSkillIds());
        c.setContextWindow(parent.getContextWindow());
        c.setCompressionThreshold(parent.getCompressionThreshold());
        c.setCompressionKeepRounds(parent.getCompressionKeepRounds());
        c.setContextUsageThreshold(parent.getContextUsageThreshold());
        c.setMaxToolResultChars(parent.getMaxToolResultChars());
        c.setEnableMemoryTools(parent.getEnableMemoryTools());
        c.setMemoryProfileMaxTokens(parent.getMemoryProfileMaxTokens());
        c.setMemoryTaskMaxTokens(parent.getMemoryTaskMaxTokens());
        c.setSandboxEnabled(parent.getSandboxEnabled());
        c.setSandboxBackend(parent.getSandboxBackend());
        c.setSandboxProviderId(parent.getSandboxProviderId());
        c.setSandboxMode(parent.getSandboxMode());
        c.setSandboxTimeout(parent.getSandboxTimeout());
        return c;
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

    private McpSyncClient createMcpClient(McpServer server) {
        String url = server.getUrl();
        if (url == null || url.isBlank()) return null;

        String baseUri;
        String path;
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost();
            int port = uri.getPort();
            baseUri = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
            String rawPath = uri.getRawPath();
            String query = uri.getRawQuery();
            path = (rawPath != null ? rawPath : "") + (query != null ? "?" + query : "");
            if (path.isEmpty()) path = "/";
        } catch (java.net.URISyntaxException e) {
            log.warn("Invalid MCP Server URL: {} - {}", url, e.getMessage());
            return null;
        }

        String transportType = server.getTransport() != null ? server.getTransport().toLowerCase() : "sse";
        io.modelcontextprotocol.spec.McpClientTransport mcpTransport;

        switch (transportType) {
            case "streamable-http", "streamable_http", "streamable" -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(baseUri)
                        .endpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
            }
            default -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(baseUri)
                        .sseEndpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
            }
        }

        return io.modelcontextprotocol.client.McpClient.sync(mcpTransport)
                .requestTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }
}
