package run.cloudclaw.admin.controller;

import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.common.dto.ChannelConfigDTO;
import run.cloudclaw.common.dto.Result;
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

import java.util.List;

/**
 * @deprecated Use {@link AdminChannelController} instead. The V2 channel management
 *             API provides full CRUD at {@code /api/admin/channels} with multi-instance
 *             support.
 */
@Deprecated
@Slf4j
@RestController
@RequestMapping("/api/admin/channel/configs")
@RequiredArgsConstructor
public class AdminChannelConfigController {

    private final ChannelConfigService channelConfigService;

    @GetMapping
    public Result<List<ChannelConfigDTO>> listConfigs() {
        return Result.ok(channelConfigService.getAllConfigs());
    }

    @GetMapping("/{channelType}")
    public Result<ChannelConfigDTO> getConfig(@PathVariable String channelType) {
        return Result.ok(channelConfigService.getConfig(channelType));
    }

    @PutMapping("/{channelType}")
    public Result<ChannelConfigDTO> saveConfig(@PathVariable String channelType,
                                                @Valid @RequestBody ChannelConfigDTO dto) {
        dto.setChannelType(channelType);
        return Result.ok(channelConfigService.saveConfig(channelType, dto));
    }

    @DeleteMapping("/{channelType}")
    public Result<Void> deleteConfig(@PathVariable String channelType) {
        channelConfigService.deleteConfig(channelType);
        return Result.ok();
    }

    /**
     * Test channel connectivity.
     * For Feishu: get tenant_access_token using appId + secret.
     */
    @PostMapping("/{channelType}/test")
    public Result<String> testConnection(@PathVariable String channelType) {
        // Will be implemented with Feishu OAuth client
        return Result.ok("Connection test not yet implemented for: " + channelType);
    }
}
