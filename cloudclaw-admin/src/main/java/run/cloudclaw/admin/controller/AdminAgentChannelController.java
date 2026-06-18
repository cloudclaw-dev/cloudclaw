package run.cloudclaw.admin.controller;

import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.common.dto.ChannelConfigDTO;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.ChannelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * Admin API for managing Agent ↔ Channel bindings.
 *
 * @deprecated Use {@link AdminChannelController} instead. The V2 channel management
 *             API provides full CRUD at {@code /api/admin/channels} with multi-instance
 *             support. This controller is kept for backward compatibility.
 */
@Deprecated
@Slf4j
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
public class AdminAgentChannelController {

    private final ChannelConfigService channelConfigService;

    @Value("${cloudclaw.auth.feishu.frontend-url:http://localhost:8080}")
    private String frontendUrl;

    /**
     * Get channel config bound to an agent.
     * @deprecated Use {@code GET /api/admin/channels?agentId=xxx} instead.
     */
    @Deprecated
    @GetMapping("/{agentId}/channel")
    public Result<ChannelConfigDTO> getAgentChannel(@PathVariable String agentId) {
        // Find config by agentId
        for (ChannelConfig config : channelConfigService.getAllEntities()) {
            if (agentId.equals(config.getAgentId())) {
                ChannelConfigDTO dto = channelConfigService.getConfig(config.getChannelType());
                dto.setWebhookUrl(buildWebhookUrl(config.getId()));
                return Result.ok(dto);
            }
        }
        // No config bound yet — return empty DTO with pre-generated webhook URL
        // Use the feishu channel_config seed ID to generate a predictable URL
        ChannelConfigDTO empty = new ChannelConfigDTO();
        empty.setChannelType("feishu");
        empty.setEnabled(false);
        empty.setWebhookUrl(frontendUrl + "/api/v1/channel/feishu/event/" + agentId);
        return Result.ok(empty);
    }

    /**
     * Bind or update channel config for an agent.
     * @deprecated Use {@code POST /api/admin/channels} to create a new channel config.
     */
    @Deprecated
    @PutMapping("/{agentId}/channel")
    public Result<ChannelConfigDTO> bindAgentChannel(@PathVariable String agentId,
                                                      @RequestBody ChannelConfigDTO dto) {
        dto.setAgentId(agentId);
        ChannelConfigDTO saved = channelConfigService.saveConfig("feishu", dto);

        // Find the saved config to get its ID for webhook URL
        ChannelConfig entity = channelConfigService.getEntity("feishu");
        saved.setWebhookUrl(buildWebhookUrl(entity.getId()));

        log.info("Agent {} bound to feishu channel", agentId);
        return Result.ok(saved);
    }

    /**
     * Unbind channel from an agent.
     * @deprecated Use {@code DELETE /api/admin/channels/{id}/agent} instead.
     */
    @Deprecated
    @DeleteMapping("/{agentId}/channel")
    public Result<Void> unbindAgentChannel(@PathVariable String agentId) {
        channelConfigService.clearAgentBinding(agentId);
        log.info("Agent {} channel binding cleared", agentId);
        return Result.ok();
    }

    /**
     * Test channel connectivity.
     * @deprecated Use {@code POST /api/admin/channels/{id}/test} instead.
     */
    @Deprecated
    @PostMapping("/{agentId}/channel/test")
    public Result<String> testAgentChannel(@PathVariable String agentId) {
        boolean ok = channelConfigService.testConnectivity("feishu");
        return Result.ok(ok ? "Connection successful" : "Connection failed");
    }

    private String buildWebhookUrl(String configId) {
        return frontendUrl + "/api/v1/channel/feishu/event/" + configId;
    }
}
