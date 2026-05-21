package run.cloudclaw.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single file within a skill's directory structure.
 *
 * <p>Skills are managed as file trees (SKILL.md + scripts/ + references/ + assets/),
 * but stored in DB for stateless deployment. Each file is stored as a row with
 * its relative path and content.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skill_files", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"skill_id", "file_path"})
})
public class SkillFile {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false, columnDefinition = "uuid")
    private Skill skill;

    /**
     * Relative file path within the skill directory, e.g. "SKILL.md", "scripts/rotate.py".
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /**
     * File content (text-based files only).
     */
    @Column(columnDefinition = "text", nullable = false)
    private String content;

    /**
     * File type hint: "text", "markdown", "python", "javascript", "json", "binary" etc.
     */
    @Column(name = "file_type", length = 20)
    private String fileType = "text";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (fileType == null) fileType = "text";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
