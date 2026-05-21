package run.cloudclaw.llm.service;

import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.model.LlmUsageStat;
import run.cloudclaw.llm.repository.LlmModelRepository;
import run.cloudclaw.llm.repository.LlmUsageStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsageService {

    private final LlmUsageStatRepository usageStatRepository;
    private final LlmModelRepository modelRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Record token usage for a request. Upserts into the daily aggregation table.
     */
    @Transactional
    public void recordUsage(String credentialId, String modelId, String userId,
                            long tokensIn, long tokensOut) {
        String today = LocalDate.now().format(DATE_FMT);

        // Calculate cost from model pricing
        double cost = calculateCost(modelId, tokensIn, tokensOut);

        LlmUsageStat stat = usageStatRepository
                .findByCredentialIdAndModelIdAndUserIdAndStatDate(credentialId, modelId, userId, today)
                .orElseGet(() -> {
                    LlmUsageStat s = new LlmUsageStat();
                    s.setCredentialId(credentialId);
                    s.setModelId(modelId);
                    s.setUserId(userId);
                    s.setStatDate(today);
                    s.setRequestCount(0);
                    s.setTokensIn(0L);
                    s.setTokensOut(0L);
                    s.setCost(0.0);
                    return s;
                });

        stat.setRequestCount(stat.getRequestCount() + 1);
        stat.setTokensIn(stat.getTokensIn() + tokensIn);
        stat.setTokensOut(stat.getTokensOut() + tokensOut);
        stat.setCost(stat.getCost() + cost);

        usageStatRepository.save(stat);
        log.debug("Recorded usage: model={}, user={}, tokensIn={}, tokensOut={}, cost={}",
                modelId, userId, tokensIn, tokensOut, cost);
    }

    private double calculateCost(String modelId, long tokensIn, long tokensOut) {
        try {
            LlmModel model = modelRepository.findById(modelId).orElse(null);
            if (model == null) return 0;
            double inPrice = model.getInputPrice() != null ? model.getInputPrice().doubleValue() : 0;
            double outPrice = model.getOutputPrice() != null ? model.getOutputPrice().doubleValue() : 0;
            return inPrice * tokensIn / 1000.0 + outPrice * tokensOut / 1000.0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get usage summary for a date range.
     */
    public Map<String, Object> getUsageSummary(String startDate, String endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(30).format(DATE_FMT);
        if (endDate == null) endDate = LocalDate.now().format(DATE_FMT);

        List<LlmUsageStat> stats = usageStatRepository.findByDateRange(startDate, endDate);

        long totalRequests = 0;
        long totalTokensIn = 0;
        long totalTokensOut = 0;
        double totalCost = 0;

        for (LlmUsageStat s : stats) {
            totalRequests += s.getRequestCount();
            totalTokensIn += s.getTokensIn();
            totalTokensOut += s.getTokensOut();
            totalCost += s.getCost();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRequests", totalRequests);
        summary.put("totalTokensIn", totalTokensIn);
        summary.put("totalTokensOut", totalTokensOut);
        summary.put("totalCost", Math.round(totalCost * 10000.0) / 10000.0);
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        return summary;
    }

    /**
     * Get usage aggregated by model.
     */
    public List<LlmUsageStat> getUsageByModel(String startDate, String endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(30).format(DATE_FMT);
        if (endDate == null) endDate = LocalDate.now().format(DATE_FMT);
        return usageStatRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Get usage aggregated by user.
     */
    public List<LlmUsageStat> getUsageByUser(String startDate, String endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(30).format(DATE_FMT);
        if (endDate == null) endDate = LocalDate.now().format(DATE_FMT);
        return usageStatRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Get daily usage trend.
     */
    public List<LlmUsageStat> getDailyUsage(String startDate, String endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(30).format(DATE_FMT);
        if (endDate == null) endDate = LocalDate.now().format(DATE_FMT);
        return usageStatRepository.findByDateRange(startDate, endDate);
    }
}
