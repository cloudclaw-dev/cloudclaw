package run.cloudclaw.agent.engine;

import run.cloudclaw.common.dto.AgentConfig;
import run.cloudclaw.common.model.McpServer;
import run.cloudclaw.common.model.Skill;
import run.cloudclaw.agent.engine.AgentTransferService.ResolvedAgent;
import run.cloudclaw.agent.tools.SkillTools;
import run.cloudclaw.agent.tools.MemoryTools;
import run.cloudclaw.sandbox.tool.SandboxExecuteTool;
import run.cloudclaw.sandbox.tool.SandboxFileTool;
import run.cloudclaw.sandbox.tool.SandboxInfoTool;
import run.cloudclaw.sandbox.core.SandboxMode;
import run.cloudclaw.mcp.repository.McpServerRepository;
import run.cloudclaw.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Shared service for resolving tool callbacks, creating MCP clients,
 * and building sub-agent configurations.
 *
 * <p>Consolidates logic previously duplicated in ChatEngine and WorkflowChatHelper.</p>
 */
@Service
@Slf4j
public class ToolResolutionService {

    private final MemoryTools memoryTools;
    private final SkillTools skillTools;
    private final SkillService skillService;
    private final McpServerRepository mcpServerRepository;
    private final SandboxExecuteTool sandboxExecuteTool;
    private final SandboxFileTool sandboxFileTool;
    private final SandboxInfoTool sandboxInfoTool;

    public ToolResolutionService(MemoryTools memoryTools,
                                  SkillTools skillTools,
                                  SkillService skillService,
                                  McpServerRepository mcpServerRepository,
                                  SandboxExecuteTool sandboxExecuteTool,
                                  SandboxFileTool sandboxFileTool,
                                  SandboxInfoTool sandboxInfoTool) {
        this.memoryTools = memoryTools;
        this.skillTools = skillTools;
        this.skillService = skillService;
        this.mcpServerRepository = mcpServerRepository;
        this.sandboxExecuteTool = sandboxExecuteTool;
        this.sandboxFileTool = sandboxFileTool;
        this.sandboxInfoTool = sandboxInfoTool;
    }

    /**
     * Resolve tool callbacks for a given agent config: memory tools, skill tools,
     * sandbox tools, and MCP server tools.
     *
     * @param config            the agent configuration
     * @param createdMcpClients list to populate with created MCP clients (caller must close)
     * @return list of resolved tool callbacks
     */
    public List<ToolCallback> resolveToolCallbacks(AgentConfig config, List<McpSyncClient> createdMcpClients) {
        List<ToolCallback> callbacks = new ArrayList<>();

        // Memory tools (enabled by default)
        if (!Boolean.FALSE.equals(config.getEnableMemoryTools())) {
            try {
                MethodToolCallbackProvider memoryProvider = MethodToolCallbackProvider.builder()
                        .toolObjects(memoryTools)
                        .build();
                callbacks.addAll(Arrays.asList(memoryProvider.getToolCallbacks()));
                log.info("Resolved {} memory tool callbacks for agent {}",
                        memoryProvider.getToolCallbacks().length, config.getAgentId());
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
                log.info("Resolved {} skill tool callbacks for agent {}",
                        provider.getToolCallbacks().length, config.getAgentId());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve skill tools: {}", e.getMessage());
        }

        // Sandbox tools + MCP Server tools
        try {
            // Sandbox tools (if enabled)
            if (Boolean.TRUE.equals(config.getSandboxEnabled())) {
                try {
                    SandboxMode mode = "SESSION".equalsIgnoreCase(config.getSandboxMode())
                            ? SandboxMode.SESSION : SandboxMode.STATELESS;

                    MethodToolCallbackProvider sandboxProvider = MethodToolCallbackProvider.builder()
                            .toolObjects(sandboxExecuteTool, sandboxInfoTool)
                            .build();
                    callbacks.addAll(Arrays.asList(sandboxProvider.getToolCallbacks()));

                    if (mode == SandboxMode.SESSION) {
                        MethodToolCallbackProvider fileProvider = MethodToolCallbackProvider.builder()
                                .toolObjects(sandboxFileTool)
                                .build();
                        callbacks.addAll(Arrays.asList(fileProvider.getToolCallbacks()));
                    }

                    log.info("Resolved sandbox tool callbacks for agent {} (mode={})", config.getAgentId(), mode);
                } catch (Exception e) {
                    log.warn("Failed to resolve sandbox tools: {}", e.getMessage());
                }
            }

            // MCP Server tools
            List<String> mcpServerIds = config.getMcpServerIds();
            if (mcpServerIds != null && !mcpServerIds.isEmpty()) {
                List<McpSyncClient> mcpClients = new ArrayList<>();
                for (String serverId : mcpServerIds) {
                    try {
                        McpServer server = mcpServerRepository.findById(UUID.fromString(serverId))
                                .orElse(null);
                        if (server == null || !Boolean.TRUE.equals(server.getEnabled())) {
                            log.warn("MCP Server {} not found or disabled", serverId);
                            continue;
                        }

                        McpSyncClient mcpClient = createMcpClient(server);
                        if (mcpClient != null) {
                            mcpClients.add(mcpClient);
                            log.info("Connected to MCP Server: {} ({})", server.getName(), server.getUrl());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to connect to MCP Server {}: {}", serverId, e.getMessage());
                    }
                }

                if (!mcpClients.isEmpty()) {
                    SyncMcpToolCallbackProvider mcpProvider = SyncMcpToolCallbackProvider.builder()
                            .mcpClients(mcpClients).build();
                    callbacks.addAll(Arrays.asList(mcpProvider.getToolCallbacks()));
                    log.info("Resolved {} MCP tool callbacks from {} servers for agent {}",
                            mcpProvider.getToolCallbacks().length, mcpClients.size(), config.getAgentId());
                }
                createdMcpClients.addAll(mcpClients);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve MCP tools: {}", e.getMessage());
        }

        log.info("Total {} tool callbacks for agent {}", callbacks.size(), config.getAgentId());
        return callbacks;
    }

    /**
     * Build a sub-agent AgentConfig from the root config and resolved sub-agent info.
     * Inherits all parent settings, overrides name, systemPrompt, modelId, mcpServerIds, skillIds.
     */
    public AgentConfig buildSubAgentConfig(AgentConfig root, ResolvedAgent sub) {
        AgentConfig c = new AgentConfig();
        c.setAgentId(root.getAgentId());
        c.setName(sub.getDisplayName());
        c.setSystemPrompt(sub.getSystemPrompt());
        c.setModelId(sub.getModelId());
        c.setTemperature(root.getTemperature());
        c.setMaxTokens(root.getMaxTokens());
        c.setMaxToolCalls(root.getMaxToolCalls());
        c.setEnabled(root.getEnabled());
        c.setMcpServerIds(sub.getMcpServerIds());
        c.setSkillIds(sub.getSkillIds());
        c.setContextWindow(root.getContextWindow());
        c.setCompressionThreshold(root.getCompressionThreshold());
        c.setCompressionKeepRounds(root.getCompressionKeepRounds());
        c.setContextUsageThreshold(root.getContextUsageThreshold());
        c.setMaxToolResultChars(root.getMaxToolResultChars());
        c.setEnableMemoryTools(root.getEnableMemoryTools());
        c.setMemoryProfileMaxTokens(root.getMemoryProfileMaxTokens());
        c.setMemoryTaskMaxTokens(root.getMemoryTaskMaxTokens());
        c.setSandboxEnabled(root.getSandboxEnabled());
        c.setSandboxBackend(root.getSandboxBackend());
        c.setSandboxProviderId(root.getSandboxProviderId());
        c.setSandboxMode(root.getSandboxMode());
        c.setSandboxTimeout(root.getSandboxTimeout());
        return c;
    }

    /**
     * Create an MCP sync client for the given server configuration.
     */
    public McpSyncClient createMcpClient(McpServer server) {
        String url = server.getUrl();

        if (url == null || url.isBlank()) {
            log.warn("MCP Server {} has no URL configured", server.getName());
            return null;
        }

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
                log.info("Using Streamable HTTP transport: baseUri={}, endpoint={}", baseUri, path);
            }
            default -> {
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(baseUri)
                        .sseEndpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
                log.info("Using SSE transport: baseUri={}, sseEndpoint={}", baseUri, path);
            }
        }

        McpSyncClient client = io.modelcontextprotocol.client.McpClient.sync(mcpTransport)
                .requestTimeout(java.time.Duration.ofSeconds(30))
                .build();

        log.info("MCP client created for server: {} ({})", server.getName(), url);
        return client;
    }

    /**
     * Close a list of MCP clients safely.
     */
    public void closeMcpClients(List<McpSyncClient> clients) {
        for (McpSyncClient client : clients) {
            try {
                client.close();
                log.debug("Closed MCP client: {}", client.getServerInfo());
            } catch (Exception e) {
                log.warn("Failed to close MCP client: {}", e.getMessage());
            }
        }
        if (!clients.isEmpty()) {
            log.info("Closed {} MCP client(s)", clients.size());
        }
    }
}
