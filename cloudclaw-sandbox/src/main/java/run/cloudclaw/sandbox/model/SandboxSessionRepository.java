package run.cloudclaw.sandbox.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SandboxSessionRepository extends JpaRepository<SandboxSession, UUID> {

    List<SandboxSession> findBySessionId(UUID sessionId);

    @Query("SELECT s FROM SandboxSession s WHERE s.status = :status")
    List<SandboxSession> findByStatus(String status);

    @Query("SELECT s FROM SandboxSession s WHERE s.sessionId = :sessionId AND s.status = 'ACTIVE'")
    List<SandboxSession> findActiveBySessionId(UUID sessionId);
}
