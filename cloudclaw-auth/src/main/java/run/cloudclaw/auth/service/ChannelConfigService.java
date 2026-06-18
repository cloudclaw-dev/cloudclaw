package run.cloudclaw.auth.service;

import run.cloudclaw.auth.repository.ChannelConfigRepository;
import run.cloudclaw.common.dto.ChannelConfigDTO;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.ChannelConfig;
import run.cloudclaw.common.util.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigRepository channelConfigRepository;
    private final CryptoService cryptoService;
    private final ObjectProvider<ChannelLifecycleManager> lifecycleManagerProvider;

    // ================================================================
    // V2: Multi-instance CRUD (by ID)
    // ================================================================

    /**
     * Create a new channel config.
     * Validates (channel_type, app_id) uniqueness.
     * If enabled and long-connection mode, starts the WS client.
     */
    @Transactional
    public ChannelConfigDTO createConfig(ChannelConfigDTO dto) {
        // Validate uniqueness
        if (dto.getAppId() != null && !dto.getAppId().isBlank()) {
            Optional<ChannelConfig> existing = channelConfigRepository
                    .findByChannelTypeAndAppId(dto.getChannelType(), dto.getAppId());
            if (existing.isPresent()) {
                throw new BusinessException(ErrorCode.CHANNEL_ALREADY_EXISTS,
                        "Channel with same type and app_id already exists: " + dto.getAppId());
            }
        }

        ChannelConfig config = new ChannelConfig();
        config.setChannelType(dto.getChannelType());
        config.setName(dto.getName());
        config.setAgentId(dto.getAgentId());
        config.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : false);
        config.setAppId(dto.getAppId());
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
            config.setAppSecretEnc(cryptoService.encrypt(dto.getAppSecret()));
        }
        config.setVerificationToken(dto.getVerificationToken());
        config.setEncryptKey(dto.getEncryptKey());
        config.setRedirectUri(dto.getRedirectUri());
        config.setExtraConfig(dto.getExtraConfig());
        config.setConnectionMode(dto.getConnectionMode() != null ? dto.getConnectionMode() : "long-connection");
        config.setPurpose(dto.getPurpose() != null ? dto.getPurpose() : "bot");
        config.setConnectionStatus("disconnected");

        config = channelConfigRepository.save(config);
        log.info("Channel config created: id={}, type={}, appId={}", config.getId(), config.getChannelType(), config.getAppId());

        // Start WS client if enabled and long-connection mode
        if (Boolean.TRUE.equals(config.getEnabled()) && isLongConnection(config)) {
            startLifecycleClient(config);
        }

        return toDTO(config);
    }

    /**
     * Update an existing channel config by ID.
     * Handles WS restart/stop/start on enable/credential changes.
     */
    @Transactional
    public ChannelConfigDTO updateConfig(String id, ChannelConfigDTO dto) {
        ChannelConfig config = channelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + id));

        boolean wasEnabled = Boolean.TRUE.equals(config.getEnabled());
        String oldAppId = config.getAppId();
        String oldSecretEnc = config.getAppSecretEnc();

        // Update fields
        if (dto.getName() != null) {
            config.setName(dto.getName());
        }
        if (dto.getAgentId() != null) {
            config.setAgentId(dto.getAgentId());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getAppId() != null) {
            config.setAppId(dto.getAppId());
        }
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()
                && !dto.getAppSecret().equals("******")) {
            config.setAppSecretEnc(cryptoService.encrypt(dto.getAppSecret()));
        }
        if (dto.getVerificationToken() != null) {
            config.setVerificationToken(dto.getVerificationToken());
        }
        if (dto.getEncryptKey() != null) {
            config.setEncryptKey(dto.getEncryptKey());
        }
        if (dto.getRedirectUri() != null) {
            config.setRedirectUri(dto.getRedirectUri());
        }
        if (dto.getExtraConfig() != null) {
            config.setExtraConfig(dto.getExtraConfig());
        }
        if (dto.getConnectionMode() != null) {
            config.setConnectionMode(dto.getConnectionMode());
        }
        if (dto.getPurpose() != null) {
            config.setPurpose(dto.getPurpose());
        }

        boolean nowEnabled = Boolean.TRUE.equals(config.getEnabled());

        // Check if credentials changed (requires WS restart)
        boolean credentialsChanged = !equalsOrNull(oldAppId, config.getAppId())
                || !equalsOrNull(oldSecretEnc, config.getAppSecretEnc());

        config = channelConfigRepository.save(config);
        log.info("Channel config updated: id={}", id);

        // Lifecycle management
        if (isLongConnection(config)) {
            if (wasEnabled && nowEnabled && credentialsChanged) {
                // Restart on credential change
                restartLifecycleClient(id);
            } else if (!wasEnabled && nowEnabled) {
                // Newly enabled
                startLifecycleClient(config);
            } else if (wasEnabled && !nowEnabled) {
                // Newly disabled
                stopLifecycleClient(id);
            }
        }

        return toDTO(config);
    }

    /**
     * Delete a channel config by ID.
     * Stops the WS client first.
     */
    @Transactional
    public void deleteConfigById(String id) {
        ChannelConfig config = channelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + id));
        // Stop WS first
        stopLifecycleClient(id);
        channelConfigRepository.delete(config);
        log.info("Channel config deleted: id={}", id);
    }

    /**
     * Find a config by ID. Returns null if not found.
     */
    public ChannelConfig findById(String id) {
        return channelConfigRepository.findById(id).orElse(null);
    }

    /**
     * Find all configs by channel type and enabled flag.
     */
    public List<ChannelConfig> findAllByChannelTypeAndEnabled(String channelType, Boolean enabled) {
        return channelConfigRepository.findAllByChannelTypeAndEnabled(channelType, enabled);
    }

    /**
     * Find enabled configs by channel type and purpose.
     * Used by FeishuAuthController (purpose=login/both) and FeishuLongConnectionManager (purpose=bot/both).
     */
    public List<ChannelConfig> findByChannelTypeEnabledAndPurpose(String channelType, List<String> purposes) {
        return channelConfigRepository.findByChannelTypeAndEnabledAndPurposeIn(
                channelType, true, purposes);
    }

    /**
     * Find the login config for a channel type (purpose=login or purpose=both).
     * Returns the first match, or null if none.
     */
    public ChannelConfig findLoginConfig(String channelType) {
        List<ChannelConfig> configs = findByChannelTypeEnabledAndPurpose(
                channelType, List.of("login", "both"));
        return configs.isEmpty() ? null : configs.get(0);
    }

    /**
     * Find all configs by agent ID.
     */
    public List<ChannelConfig> findAllByAgentId(String agentId) {
        return channelConfigRepository.findAllByAgentId(agentId);
    }

    /**
     * Find config by channel type and app ID.
     */
    public Optional<ChannelConfig> findByChannelTypeAndAppId(String channelType, String appId) {
        return channelConfigRepository.findByChannelTypeAndAppId(channelType, appId);
    }

    /**
     * Update connection status for a config.
     */
    @Transactional
    public void updateConnectionStatus(String id, String status) {
        channelConfigRepository.findById(id).ifPresent(config -> {
            config.setConnectionStatus(status);
            if ("connected".equals(status)) {
                config.setLastConnectedAt(LocalDateTime.now());
            }
            channelConfigRepository.save(config);
        });
    }

    /**
     * Bind an agent to a channel config.
     */
    @Transactional
    public ChannelConfigDTO bindAgent(String configId, String agentId) {
        ChannelConfig config = channelConfigRepository.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + configId));
        config.setAgentId(agentId);
        config = channelConfigRepository.save(config);
        log.info("Agent {} bound to channel config {}", agentId, configId);
        return toDTO(config);
    }

    /**
     * Unbind agent from a channel config.
     */
    @Transactional
    public ChannelConfigDTO unbindAgent(String configId) {
        ChannelConfig config = channelConfigRepository.findById(configId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + configId));
        config.setAgentId(null);
        config = channelConfigRepository.save(config);
        log.info("Agent unbound from channel config {}", configId);
        return toDTO(config);
    }

    /**
     * Get all configs as DTOs.
     */
    public List<ChannelConfigDTO> getAllConfigs() {
        return channelConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get configs filtered by channel type.
     */
    public List<ChannelConfigDTO> getConfigsByChannelType(String channelType) {
        return channelConfigRepository.findAll().stream()
                .filter(c -> channelType == null || channelType.equals(c.getChannelType()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get decrypted secret for a config entity.
     */
    public String getDecryptedSecret(ChannelConfig config) {
        if (config.getAppSecretEnc() == null || config.getAppSecretEnc().isBlank()) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_CONFIGURED, "Channel secret not configured");
        }
        return cryptoService.decrypt(config.getAppSecretEnc());
    }

    /**
     * Get decrypted app secret for internal use (OAuth flow).
     * @deprecated Use {@link #getDecryptedSecret(ChannelConfig)} with a specific config entity.
     */
    @Deprecated
    public String getDecryptedSecret(String channelType) {
        ChannelConfig config = channelConfigRepository.findByChannelType(channelType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_CONFIGURED, "Channel not configured: " + channelType));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.CHANNEL_DISABLED, "Channel disabled: " + channelType);
        }
        if (config.getAppSecretEnc() == null || config.getAppSecretEnc().isBlank()) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_CONFIGURED, "Channel secret not configured: " + channelType);
        }
        return cryptoService.decrypt(config.getAppSecretEnc());
    }

    /**
     * Check if a channel type is configured and enabled.
     * @deprecated In V2, multiple configs can exist per channel type.
     */
    @Deprecated
    public boolean isChannelEnabled(String channelType) {
        return channelConfigRepository.findByChannelType(channelType)
                .map(c -> Boolean.TRUE.equals(c.getEnabled()) && c.getAppId() != null)
                .orElse(false);
    }

    /**
     * Get the full config entity for internal use.
     * @deprecated In V2, use {@link #findById(String)} instead.
     */
    @Deprecated
    public ChannelConfig getEntity(String channelType) {
        return channelConfigRepository.findByChannelType(channelType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_CONFIGURED, "Channel not configured: " + channelType));
    }

    /**
     * Get all config entities (for internal lookups).
     */
    public List<ChannelConfig> getAllEntities() {
        return channelConfigRepository.findAll();
    }

    /**
     * Get a config DTO by channel type.
     * @deprecated Use {@link #getConfigsByChannelType(String)} or {@link #findById(String)}.
     */
    @Deprecated
    public ChannelConfigDTO getConfig(String channelType) {
        ChannelConfig config = channelConfigRepository.findByChannelType(channelType)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + channelType));
        return toDTO(config);
    }

    /**
     * Clear agent binding for a specific agent across all configs.
     */
    @Transactional
    public void clearAgentBinding(String agentId) {
        List<ChannelConfig> configs = channelConfigRepository.findAllByAgentId(agentId);
        for (ChannelConfig config : configs) {
            config.setAgentId(null);
            // Don't auto-disable in V2 — just unbind
            channelConfigRepository.save(config);
        }
    }

    /**
     * Test connectivity by checking if secret can be decrypted.
     */
    public boolean testConnectivity(String channelType) {
        try {
            ChannelConfig config = channelConfigRepository.findByChannelType(channelType)
                    .orElse(null);
            if (config == null) return false;
            getDecryptedSecret(config);
            return config.getAppId() != null && !config.getAppId().isBlank();
        } catch (Exception e) {
            log.warn("Channel connectivity test failed for {}: {}", channelType, e.getMessage());
            return false;
        }
    }

    /**
     * Test connectivity for a specific config by ID.
     */
    public boolean testConnectivityById(String id) {
        try {
            ChannelConfig config = channelConfigRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + id));
            getDecryptedSecret(config);
            return config.getAppId() != null && !config.getAppId().isBlank();
        } catch (Exception e) {
            log.warn("Channel connectivity test failed for id={}: {}", id, e.getMessage());
            return false;
        }
    }

    // ================================================================
    // V1 backward-compat methods (deprecated)
    // ================================================================

    /**
     * @deprecated Use {@link #createConfig(ChannelConfigDTO)} or {@link #updateConfig(String, ChannelConfigDTO)}.
     */
    @Deprecated
    @Transactional
    public ChannelConfigDTO saveConfig(String channelType, ChannelConfigDTO dto) {
        ChannelConfig config = channelConfigRepository.findByChannelType(channelType)
                .orElseGet(() -> {
                    ChannelConfig c = new ChannelConfig();
                    c.setChannelType(channelType);
                    return c;
                });

        config.setEnabled(dto.getEnabled());
        config.setAppId(dto.getAppId());
        config.setAgentId(dto.getAgentId());
        if (dto.getName() != null) {
            config.setName(dto.getName());
        }
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()
                && !dto.getAppSecret().equals("******")) {
            config.setAppSecretEnc(cryptoService.encrypt(dto.getAppSecret()));
        }
        config.setVerificationToken(dto.getVerificationToken());
        config.setEncryptKey(dto.getEncryptKey());
        config.setRedirectUri(dto.getRedirectUri());
        config.setExtraConfig(dto.getExtraConfig());
        if (dto.getConnectionMode() != null) {
            config.setConnectionMode(dto.getConnectionMode());
        }
        if (dto.getPurpose() != null) {
            config.setPurpose(dto.getPurpose());
        }

        config = channelConfigRepository.save(config);
        log.info("Channel config saved for type: {}", channelType);
        return toDTO(config);
    }

    /**
     * @deprecated Use {@link #deleteConfigById(String)}.
     */
    @Deprecated
    @Transactional
    public void deleteConfig(String channelType) {
        ChannelConfig config = channelConfigRepository.findByChannelType(channelType)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Channel config not found: " + channelType));
        // Stop WS if running
        stopLifecycleClient(config.getId());
        channelConfigRepository.delete(config);
        log.info("Channel config deleted for type: {}", channelType);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private boolean isLongConnection(ChannelConfig config) {
        return config.getConnectionMode() == null
                || "long-connection".equals(config.getConnectionMode());
    }

    private void startLifecycleClient(ChannelConfig config) {
        try {
            ChannelLifecycleManager manager = lifecycleManagerProvider.getIfAvailable();
            if (manager != null) {
                manager.startClient(config);
            }
        } catch (Exception e) {
            log.error("Failed to start lifecycle client for configId={}: {}", config.getId(), e.getMessage(), e);
            updateConnectionStatus(config.getId(), "error");
        }
    }

    private void stopLifecycleClient(String configId) {
        try {
            ChannelLifecycleManager manager = lifecycleManagerProvider.getIfAvailable();
            if (manager != null) {
                manager.stopClient(configId);
            }
        } catch (Exception e) {
            log.warn("Failed to stop lifecycle client for configId={}: {}", configId, e.getMessage());
        }
    }

    private void restartLifecycleClient(String configId) {
        try {
            ChannelLifecycleManager manager = lifecycleManagerProvider.getIfAvailable();
            if (manager != null) {
                manager.restartClient(configId);
            }
        } catch (Exception e) {
            log.error("Failed to restart lifecycle client for configId={}: {}", configId, e.getMessage(), e);
        }
    }

    private static boolean equalsOrNull(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public ChannelConfigDTO toDTO(ChannelConfig config) {
        return ChannelConfigDTO.builder()
                .id(config.getId())
                .channelType(config.getChannelType())
                .name(config.getName())
                .enabled(config.getEnabled())
                .appId(config.getAppId())
                .appSecret(config.getAppSecretEnc() != null ? "******" : null)
                .verificationToken(config.getVerificationToken())
                .encryptKey(config.getEncryptKey())
                .redirectUri(config.getRedirectUri())
                .extraConfig(config.getExtraConfig())
                .agentId(config.getAgentId())
                .connectionMode(config.getConnectionMode())
                .connectionStatus(config.getConnectionStatus())
                .lastConnectedAt(config.getLastConnectedAt())
                .purpose(config.getPurpose())
                .build();
    }
}
