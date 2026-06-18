package run.cloudclaw.admin.controller;

import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.auth.service.ChannelLifecycleManager;
import run.cloudclaw.common.dto.ChannelConfigDTO;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.model.ChannelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V2 unified Admin Channel management REST API.
 *
 * <p>Replaces the old {@link AdminAgentChannelController} pattern where channel
 * configuration was embedded under agent endpoints. This controller provides
 * a standalone CRUD + lifecycle management surface for all channel types.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/channels")
@RequiredArgsConstructor
public class AdminChannelController {

    private final ChannelConfigService channelConfigService;
    private final ObjectProvider<ChannelLifecycleManager> lifecycleManagerProvider;

    @Value("${cloudclaw.auth.feishu.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    // ===== CRUD =====

    /**
     * List all channel configs, optionally filtered by channelType or agentId.
     */
    @GetMapping
    public Result<List<ChannelConfigDTO>> list(
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) String agentId) {

        List<ChannelConfig> configs;
        if (agentId != null && !agentId.isBlank()) {
            configs = channelConfigService.findAllByAgentId(agentId);
        } else {
            configs = channelConfigService.getAllEntities();
        }

        List<ChannelConfigDTO> dtos = configs.stream()
                .filter(c -> channelType == null || channelType.isBlank() || channelType.equals(c.getChannelType()))
                .map(config -> {
                    ChannelConfigDTO dto = channelConfigService.toDTO(config);
                    dto.setWebhookUrl(buildWebhookUrl(config));
                    return dto;
                })
                .collect(Collectors.toList());

        return Result.ok(dtos);
    }

    /**
     * Create a new channel config.
     */
    @PostMapping
    public Result<ChannelConfigDTO> create(@RequestBody ChannelConfigDTO dto) {
        ChannelConfigDTO created = channelConfigService.createConfig(dto);
        created.setWebhookUrl(frontendUrl + "/api/v1/channel/feishu/event/" + created.getId());
        return Result.ok(created);
    }

    /**
     * Get a channel config by ID.
     */
    @GetMapping("/{id}")
    public Result<ChannelConfigDTO> get(@PathVariable String id) {
        ChannelConfig config = channelConfigService.findById(id);
        if (config == null) {
            return Result.error(404, "Channel config not found: " + id);
        }
        ChannelConfigDTO dto = channelConfigService.toDTO(config);
        dto.setWebhookUrl(buildWebhookUrl(config));
        return Result.ok(dto);
    }

    /**
     * Update a channel config by ID.
     */
    @PutMapping("/{id}")
    public Result<ChannelConfigDTO> update(@PathVariable String id,
                                             @RequestBody ChannelConfigDTO dto) {
        ChannelConfigDTO updated = channelConfigService.updateConfig(id, dto);
        updated.setWebhookUrl(frontendUrl + "/api/v1/channel/feishu/event/" + id);
        return Result.ok(updated);
    }

    /**
     * Delete a channel config by ID.
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        channelConfigService.deleteConfigById(id);
        return Result.ok();
    }

    // ===== Connection management =====

    /**
     * Manually start WS connection for a channel config.
     */
    @PostMapping("/{id}/connect")
    public Result<Void> connect(@PathVariable String id) {
        ChannelConfig config = channelConfigService.findById(id);
        if (config == null) {
            return Result.error(404, "Channel config not found: " + id);
        }
        ChannelLifecycleManager mgr = lifecycleManagerProvider.getIfAvailable();
        if (mgr != null) {
            mgr.startClient(config);
        } else {
            return Result.error(400, "Long-connection mode is not available");
        }
        return Result.ok();
    }

    /**
     * Manually stop WS connection for a channel config.
     */
    @PostMapping("/{id}/disconnect")
    public Result<Void> disconnect(@PathVariable String id) {
        ChannelLifecycleManager mgr = lifecycleManagerProvider.getIfAvailable();
        if (mgr != null) {
            mgr.stopClient(id);
        }
        return Result.ok();
    }

    /**
     * Get connection status for a channel config.
     */
    @GetMapping("/{id}/status")
    public Result<Map<String, String>> status(@PathVariable String id) {
        ChannelConfig config = channelConfigService.findById(id);
        Map<String, String> result = new HashMap<>();
        if (config == null) {
            result.put("status", "not_found");
            return Result.ok(result);
        }
        result.put("status", config.getConnectionStatus() != null ? config.getConnectionStatus() : "unknown");
        result.put("connectionMode", config.getConnectionMode() != null ? config.getConnectionMode() : "unknown");

        ChannelLifecycleManager mgr = lifecycleManagerProvider.getIfAvailable();
        if (mgr != null) {
            result.put("wsActive", String.valueOf(mgr.isHandledByWs(id)));
        } else {
            result.put("wsActive", "false");
        }
        return Result.ok(result);
    }

    /**
     * Test connectivity (verify appId/secret are valid).
     */
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable String id) {
        boolean ok = channelConfigService.testConnectivityById(id);
        return Result.ok(ok ? "Connection test successful" : "Connection test failed");
    }

    // ===== Agent binding =====

    /**
     * Bind an agent to this channel config.
     */
    @PutMapping("/{id}/agent")
    public Result<ChannelConfigDTO> bindAgent(@PathVariable String id,
                                                @RequestParam String agentId) {
        ChannelConfigDTO dto = channelConfigService.bindAgent(id, agentId);
        return Result.ok(dto);
    }

    /**
     * Unbind agent from this channel config.
     */
    @DeleteMapping("/{id}/agent")
    public Result<ChannelConfigDTO> unbindAgent(@PathVariable String id) {
        ChannelConfigDTO dto = channelConfigService.unbindAgent(id);
        return Result.ok(dto);
    }

    // ===== Helper =====

    private String buildWebhookUrl(ChannelConfig config) {
        String type = config.getChannelType();
        if ("feishu".equals(type)) {
            return frontendUrl + "/api/v1/channel/feishu/event/" + config.getId();
        }
        // Generic pattern for future channel types
        return frontendUrl + "/api/v1/channel/" + type + "/event/" + config.getId();
    }
}
