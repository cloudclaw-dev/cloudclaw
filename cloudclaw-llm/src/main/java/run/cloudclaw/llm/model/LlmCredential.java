package run.cloudclaw.llm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "llm_credentials")
public class LlmCredential {

    @jakarta.persistence.Id
    @Column(length = 36)
    private String id;

    @Column(name = "provider_id", nullable = false, length = 36)
    private String providerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "api_key_encrypted", nullable = false, length = 500)
    private String apiKeyEncrypted;

    @Column
    private Integer weight = 100;

    @Column
    private Integer priority = 1;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "rate_limit_rpm")
    private Integer rateLimitRpm;

    @Column(name = "rate_limit_tpm")
    private Integer rateLimitTpm;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (weight == null) {
            weight = 100;
        }
        if (priority == null) {
            priority = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
