package run.cloudclaw.llm.repository;

import run.cloudclaw.llm.model.LlmUsageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LlmUsageStatRepository extends JpaRepository<LlmUsageStat, String> {

    Optional<LlmUsageStat> findByCredentialIdAndModelIdAndUserIdAndStatDate(
            String credentialId, String modelId, String userId, String statDate);

    @Query("SELECT s FROM LlmUsageStat s WHERE s.statDate BETWEEN :startDate AND :endDate ORDER BY s.statDate ASC")
    List<LlmUsageStat> findByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Query("SELECT s FROM LlmUsageStat s WHERE s.userId = :userId AND s.statDate BETWEEN :startDate AND :endDate ORDER BY s.statDate DESC")
    List<LlmUsageStat> findByUserIdAndDateRange(@Param("userId") String userId,
                                                 @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate);

    @Query("SELECT s FROM LlmUsageStat s WHERE s.modelId = :modelId AND s.statDate BETWEEN :startDate AND :endDate ORDER BY s.statDate DESC")
    List<LlmUsageStat> findByModelIdAndDateRange(@Param("modelId") String modelId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);

    @Query("SELECT s.modelId, SUM(s.requestCount), SUM(s.tokensIn), SUM(s.tokensOut), SUM(s.cost) FROM LlmUsageStat s WHERE s.statDate BETWEEN :startDate AND :endDate GROUP BY s.modelId ORDER BY SUM(s.cost) DESC")
    List<Object[]> aggregateByModel(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Query("SELECT s.userId, SUM(s.requestCount), SUM(s.tokensIn), SUM(s.tokensOut), SUM(s.cost) FROM LlmUsageStat s WHERE s.statDate BETWEEN :startDate AND :endDate GROUP BY s.userId ORDER BY SUM(s.cost) DESC")
    List<Object[]> aggregateByUser(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Query("SELECT s.statDate, SUM(s.requestCount), SUM(s.tokensIn), SUM(s.tokensOut), SUM(s.cost) FROM LlmUsageStat s WHERE s.statDate BETWEEN :startDate AND :endDate GROUP BY s.statDate ORDER BY s.statDate")
    List<Object[]> aggregateByDate(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
