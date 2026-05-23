package run.cloudclaw.common.repository;

import run.cloudclaw.common.model.SessionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionItemRepository extends JpaRepository<SessionItem, String> {
    List<SessionItem> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<SessionItem> findByUserIdOrderByCreatedAtAsc(String userId);
    void deleteBySessionIdAndId(String sessionId, String id);
    void deleteBySessionId(String sessionId);
}
