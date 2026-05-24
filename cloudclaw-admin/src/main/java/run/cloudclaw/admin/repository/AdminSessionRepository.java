package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin repository for Session entity access.
 */
public interface AdminSessionRepository extends JpaRepository<Session, String> {

    long countByUserId(String userId);
    void deleteByAgentId(String agentId);

    long countByUpdatedAtAfter(LocalDateTime threshold);

    /** Aggregate session count per creation date within a date range. */
    @Query("SELECT CAST(s.createdAt AS date), COUNT(s) FROM Session s WHERE s.createdAt >= :start AND s.createdAt < :end GROUP BY CAST(s.createdAt AS date)")
    List<Object[]> countByDateRange(@org.springframework.data.repository.query.Param("start") LocalDateTime start, @org.springframework.data.repository.query.Param("end") LocalDateTime end);
}
