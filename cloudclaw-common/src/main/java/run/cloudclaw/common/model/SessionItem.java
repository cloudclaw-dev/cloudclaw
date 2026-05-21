package run.cloudclaw.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "session_items")
public class SessionItem {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(insertable = false, updatable = false)
    private Integer tokens;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
