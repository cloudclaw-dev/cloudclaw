package run.cloudclaw.common.repository;

import run.cloudclaw.common.model.PromptLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PromptLogRepository extends JpaRepository<PromptLog, String> {

    Page<PromptLog> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<PromptLog> findBySessionIdAndCreatedAtBetween(String sessionId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<PromptLog> findByAgentIdAndCreatedAtBetween(String agentId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    Page<PromptLog> findBySessionIdAndAgentIdAndCreatedAtBetween(String sessionId, String agentId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query("SELECT p FROM PromptLog p WHERE CAST(p.content AS string) LIKE CONCAT('%', CAST(:keyword AS string), '%') AND p.createdAt BETWEEN :startTime AND :endTime")
    Page<PromptLog> searchByKeyword(@Param("keyword") String keyword, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime, Pageable pageable);
}
