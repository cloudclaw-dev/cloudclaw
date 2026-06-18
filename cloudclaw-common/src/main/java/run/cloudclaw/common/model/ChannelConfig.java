package run.cloudclaw.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channel_config")
public class ChannelConfig {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType;

    @Column(length = 100)
    private String name;

    @Column(name = "agent_id", length = 36)
    private String agentId;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(name = "app_id", length = 200)
    private String appId;

    @Column(name = "app_secret_enc", length = 500)
    private String appSecretEnc;

    @Column(name = "verification_token", length = 200)
    private String verificationToken;

    @Column(name = "encrypt_key", length = 200)
    private String encryptKey;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;

    @Column(name = "connection_mode", length = 20)
    private String connectionMode = "long-connection";

    @Column(name = "connection_status", length = 20)
    private String connectionStatus = "disconnected";

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @Column(name = "purpose", length = 20)
    private String purpose = "bot";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (enabled == null) enabled = false;
        if (connectionMode == null) connectionMode = "long-connection";
        if (connectionStatus == null) connectionStatus = "disconnected";
        if (purpose == null) purpose = "bot";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
