package run.cloudclaw.common.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a skill following the Claude Agent Skill standard.
 *
 * <p>A skill is logically a directory containing:</p>
 * <ul>
 *   <li>SKILL.md (required) — YAML frontmatter (name, description) + markdown instructions</li>
 *   <li>scripts/ (optional) — executable code</li>
 *   <li>references/ (optional) — reference docs loaded on demand</li>
 *   <li>assets/ (optional) — templates and resources</li>
 * </ul>
 *
 * <p>Stored in DB for stateless deployment. Individual files are in {@link SkillFile}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /**
     * Description from SKILL.md frontmatter. Used as the primary triggering mechanism
     * for LLM-based skill matching.
     */
    @Column(columnDefinition = "text")
    private String description;

    /**
     * Instructions from SKILL.md body. Loaded into system prompt when skill is triggered.
     */
    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SkillFile> files = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
