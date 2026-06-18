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
@Table(name = "feishu_conversation")
public class FeishuConversation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "channel_config_id", nullable = false, length = 36)
    private String channelConfigId;

    @Column(name = "feishu_chat_id", nullable = false, length = 100)
    private String feishuChatId;

    @Column(name = "feishu_chat_type", nullable = false, length = 20)
    private String feishuChatType = "p2p";

    @Column(name = "feishu_user_id", nullable = false, length = 100)
    private String feishuUserId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
