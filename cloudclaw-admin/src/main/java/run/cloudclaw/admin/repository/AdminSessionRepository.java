package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Admin repository for Session entity access.
 */
public interface AdminSessionRepository extends JpaRepository<Session, String> {

    long countByUserId(String userId);
    void deleteByAgentId(String agentId);
}
