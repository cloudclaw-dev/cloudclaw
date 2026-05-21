    package run.cloudclaw.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "agents")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "model_id", length = 100)
    private String modelId;

    @Column
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "max_tool_calls")
    private Integer maxToolCalls;

    /** Max conversation rounds before triggering summary compression */
    @Column(name = "compression_threshold")
    private Integer compressionThreshold;

    /** Number of recent rounds to keep when compressing */
    @Column(name = "compression_keep_rounds")
    private Integer compressionKeepRounds;

    /** Token usage threshold (0.0-1.0) for dynamic context compression */
    @Column(name = "context_usage_threshold")
    private Double contextUsageThreshold;

    /** Max characters for tool result content (default 3000). Truncated if exceeded. */
    @Column(name = "max_tool_result_chars")
    private Integer maxToolResultChars;

    /** Whether to enable memory management tools for this agent */
    @Column(name = "enable_memory_tools")
    private Boolean enableMemoryTools;

    /** Max tokens for user profile memory (default 2000) */
    @Column(name = "memory_profile_max_tokens")
    private Integer memoryProfileMaxTokens;

    /** Max tokens for task memory (default 1000) */
    @Column(name = "memory_task_max_tokens")
    private Integer memoryTaskMaxTokens;

    // ========== Sandbox fields ==========

    /** Whether to enable sandbox code execution for this agent */
    @Column(name = "sandbox_enabled")
    private Boolean sandboxEnabled;

    /** Sandbox backend: LOCAL, DOCKER, E2B */
    @Column(name = "sandbox_backend", length = 20)
    private String sandboxBackend;

    /** Reference to sandbox provider */
    @Column(name = "sandbox_provider_id")
    private String sandboxProviderId;

    /** Sandbox mode: STATELESS or SESSION */
    @Column(name = "sandbox_mode", length = 20)
    private String sandboxMode;

    /** Default execution timeout in seconds */
    @Column(name = "sandbox_timeout")
    private Integer sandboxTimeout;

    @Column(name = "created_by", nullable = false, columnDefinition = "uuid")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID createdBy;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private java.util.List<String> mcpServerIds;

    @Transient
    private java.util.List<String> skillIds;

    @PrePersist
    protected void onCreate() {
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
