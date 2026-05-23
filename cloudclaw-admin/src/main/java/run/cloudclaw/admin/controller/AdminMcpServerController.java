package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.dto.CreateMcpServerRequest;
import run.cloudclaw.admin.dto.UpdateMcpServerRequest;
import run.cloudclaw.admin.repository.AdminMcpServerRepository;
import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.config.ConfigChangeNotifier;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.model.McpServer;
import run.cloudclaw.common.util.CryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/mcp-servers")
@RequiredArgsConstructor
public class AdminMcpServerController {

    private final AdminMcpServerRepository mcpServerRepository;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final ConfigChangeNotifier configChangeNotifier;

    @PostMapping
    public Result<McpServer> createMcpServer(@Valid @RequestBody CreateMcpServerRequest request) {
        log.info("Admin registering MCP server with name: {}", request.getName());

        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setTransport(request.getTransport());
        server.setUrl(request.getUrl());
        server.setCommand(request.getCommand());
        server.setEnabled(true);

        try {
            if (request.getArgs() != null) {
                server.setArgs(objectMapper.writeValueAsString(request.getArgs()));
            }
            if (request.getEnv() != null) {
                String envJson = objectMapper.writeValueAsString(request.getEnv());
                server.setEnv(cryptoService.encrypt(envJson));
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "Failed to serialize args/env to JSON: " + e.getMessage());
        }

        McpServer saved = mcpServerRepository.save(server);
        log.info("MCP server registered successfully with id: {}", saved.getId());
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.CREATE, "mcp", saved.getId().toString());
        return Result.ok(maskEnv(saved));
    }

    @GetMapping
    public Result<List<McpServer>> listMcpServers() {
        log.debug("Admin listing all MCP servers");
        List<McpServer> servers = mcpServerRepository.findAll();
        List<McpServer> masked = servers.stream().map(this::maskEnv).toList();
        return Result.ok(masked);
    }

    @PutMapping("/{id}")
    public Result<McpServer> updateMcpServer(@PathVariable String id,
                                             @Valid @RequestBody UpdateMcpServerRequest request) {
        log.info("Admin updating MCP server with id: {}", id);

        UUID serverId = UUID.fromString(id);
        McpServer server = mcpServerRepository.findById(serverId)
                .orElseThrow(() -> new BusinessException(404, "MCP server not found: " + id));

        if (request.getName() != null) server.setName(request.getName());
        if (request.getDescription() != null) server.setDescription(request.getDescription());
        if (request.getTransport() != null) server.setTransport(request.getTransport());
        if (request.getUrl() != null) server.setUrl(request.getUrl());
        if (request.getCommand() != null) server.setCommand(request.getCommand());

        try {
            if (request.getArgs() != null) {
                server.setArgs(objectMapper.writeValueAsString(request.getArgs()));
            }
            if (request.getEnv() != null) {
                String envJson = objectMapper.writeValueAsString(request.getEnv());
                server.setEnv(cryptoService.encrypt(envJson));
            }
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "Failed to serialize args/env to JSON: " + e.getMessage());
        }

        McpServer saved = mcpServerRepository.save(server);
        log.info("MCP server updated successfully: {}", id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.UPDATE, "mcp", id);
        return Result.ok(maskEnv(saved));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteMcpServer(@PathVariable String id) {
        log.info("Admin deleting MCP server with id: {}", id);
        UUID serverId = UUID.fromString(id);
        if (!mcpServerRepository.existsById(serverId)) {
            throw new BusinessException(404, "MCP server not found: " + id);
        }
        mcpServerRepository.deleteById(serverId);
        log.info("MCP server deleted successfully: {}", id);
        configChangeNotifier.notifyChange(ConfigChangeEvent.ChangeType.DELETE, "mcp", id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnection(@PathVariable String id) {
        log.info("Admin testing connection to MCP server: {}", id);

        UUID serverId = UUID.fromString(id);
        McpServer server = mcpServerRepository.findById(serverId)
                .orElseThrow(() -> new BusinessException(404, "MCP server not found: " + id));

        Map<String, Object> result = new HashMap<>();

        String url = server.getUrl();
        String transport = server.getTransport() != null ? server.getTransport().toLowerCase() : "sse";

        if (("sse".equals(transport) || "streamable-http".equals(transport) || "streamable_http".equals(transport))
                && (url == null || url.isBlank())) {
            result.put("connected", false);
            result.put("error", "URL is required for " + transport + " transport");
            return Result.ok(result);
        }

        io.modelcontextprotocol.client.McpSyncClient client = null;
        try {
            io.modelcontextprotocol.spec.McpClientTransport mcpTransport;
            if ("streamable-http".equals(transport) || "streamable_http".equals(transport) || "streamable".equals(transport)) {
                java.net.URI uri = new java.net.URI(url);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String host = uri.getHost();
                int port = uri.getPort();
                String baseUri = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
                String path = (uri.getRawPath() != null ? uri.getRawPath() : "/") + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(baseUri)
                        .endpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
            } else {
                java.net.URI uri = new java.net.URI(url);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String host = uri.getHost();
                int port = uri.getPort();
                String baseUri = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
                String path = (uri.getRawPath() != null ? uri.getRawPath() : "/") + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                mcpTransport = io.modelcontextprotocol.client.transport.HttpClientSseClientTransport.builder(baseUri)
                        .sseEndpoint(path)
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build();
            }

            client = io.modelcontextprotocol.client.McpClient.sync(mcpTransport)
                    .requestTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            client.initialize();

            var toolsResponse = client.listTools();
            List<Map<String, Object>> tools = toolsResponse.tools().stream()
                    .map(tool -> {
                        Map<String, Object> t = new HashMap<>();
                        t.put("name", tool.name());
                        t.put("description", tool.description());
                        return t;
                    }).toList();

            result.put("connected", true);
            result.put("serverName", client.getServerInfo().name());
            result.put("serverVersion", client.getServerInfo().version());
            result.put("tools", tools);
            result.put("toolCount", tools.size());
            log.info("MCP server test OK: {}, server={}, tools={}", id, client.getServerInfo().name(), tools.size());
        } catch (Exception e) {
            log.warn("MCP server connection test failed for {}: {}", id, e.getMessage());
            result.put("connected", false);
            result.put("error", e.getMessage());
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
        }

        return Result.ok(result);
    }

    /**
     * Mask sensitive values in the env JSON for API responses.
     */
    private McpServer maskEnv(McpServer server) {
        // Create a shallow copy to avoid mutating the JPA entity
        McpServer copy = new McpServer();
        copy.setId(server.getId());
        copy.setName(server.getName());
        copy.setDescription(server.getDescription());
        copy.setUrl(server.getUrl());
        copy.setEnabled(server.getEnabled());
        copy.setTransport(server.getTransport());
        copy.setCommand(server.getCommand());
        copy.setArgs(server.getArgs());
        copy.setCreatedAt(server.getCreatedAt());
        copy.setUpdatedAt(server.getUpdatedAt());
        copy.setEnv(server.getEnv()); // will be masked below

        if (server.getEnv() == null || server.getEnv().isEmpty()) {
            return copy;
        }
        try {
            String envJson = cryptoService.decrypt(server.getEnv());
            JsonNode node = objectMapper.readTree(envJson);
            if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;
                obj.fieldNames().forEachRemaining(key -> {
                    String value = node.get(key).asText("");
                    if (value.length() > 4) {
                        obj.put(key, "****" + value.substring(value.length() - 4));
                    } else {
                        obj.put(key, "****");
                    }
                });
                copy.setEnv(objectMapper.writeValueAsString(node));
            }
        } catch (Exception e) {
            // If decryption/parsing fails, just mask the whole thing
            server.setEnv("****");
        }
        return server;
    }
}
