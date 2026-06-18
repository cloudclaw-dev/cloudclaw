package run.cloudclaw.auth.repository;

import run.cloudclaw.common.model.FeishuConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeishuConversationRepository extends JpaRepository<FeishuConversation, String> {

    Optional<FeishuConversation> findByChannelConfigIdAndFeishuChatIdAndFeishuUserId(
            String channelConfigId, String feishuChatId, String feishuUserId);

    void deleteBySessionId(String sessionId);
}
