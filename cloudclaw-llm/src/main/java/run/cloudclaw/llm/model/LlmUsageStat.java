package run.cloudclaw.llm.model;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_usage_stats")
public class LlmUsageStat {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "credential_id", nullable = false, length = 36)
    private String credentialId;

    @Column(name = "model_id", nullable = false, length = 36)
    private String modelId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "stat_date", nullable = false, length = 10)
    private String statDate;

    @Column(name = "request_count")
    private Integer requestCount = 0;

    @Column(name = "tokens_in")
    private Long tokensIn = 0L;

    @Column(name = "tokens_out")
    private Long tokensOut = 0L;

    @Column
    private Double cost = 0.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
