package run.cloudclaw.session.repository;

import run.cloudclaw.common.model.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Session entity operations.
 */
public interface SessionRepository extends JpaRepository<Session, String> {

    List<Session> findByUserIdOrderByUpdatedAtDesc(String userId);

    Page<Session> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    Page<Session> findByUserIdAndAgentIdOrderByUpdatedAtDesc(String userId, String agentId, Pageable pageable);
}
