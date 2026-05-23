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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_models")
public class LlmModel {

    @jakarta.persistence.Id
    @Column(length = 36)
    private String id;

    @Column(name = "provider_id", nullable = false, length = 36)
    private String providerId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "model_type", nullable = false, length = 20)
    private String modelType;

    @Column(name = "context_window")
    private Integer contextWindow;

    @Column(name = "max_output")
    private Integer maxOutput;

    @Column(name = "input_price", precision = 10, scale = 6)
    private BigDecimal inputPrice;

    @Column(name = "output_price", precision = 10, scale = 6)
    private BigDecimal outputPrice;

    @Column(columnDefinition = "TEXT")
    private String capabilities;

    @Column(name = "default_params", columnDefinition = "TEXT")
    private String defaultParams;

    @Column(nullable = false)
    private Boolean enabled = true;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
