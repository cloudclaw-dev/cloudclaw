package run.cloudclaw.llm.controller;

import run.cloudclaw.common.dto.Result;
import run.cloudclaw.llm.model.LlmUsageStat;
import run.cloudclaw.llm.service.LlmUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/llm/usage")
@RequiredArgsConstructor
public class AdminLlmUsageController {

    private final LlmUsageService usageService;

    /**
     * Get usage summary (totals for a date range).
     */
    @GetMapping
    public Result<Map<String, Object>> getUsageSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(usageService.getUsageSummary(startDate, endDate));
    }

    /**
     * Get usage aggregated by model.
     */
    @GetMapping("/by-model")
    public Result<List<Map<String, Object>>> getUsageByModel(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<LlmUsageStat> stats = usageService.getUsageByModel(startDate, endDate);

        // Aggregate by modelId
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (LlmUsageStat s : stats) {
            aggregated.computeIfAbsent(s.getModelId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("modelId", k);
                m.put("requestCount", 0);
                m.put("tokensIn", 0L);
                m.put("tokensOut", 0L);
                m.put("cost", 0.0);
                return m;
            });
            Map<String, Object> m = aggregated.get(s.getModelId());
            m.put("requestCount", (int) m.get("requestCount") + s.getRequestCount());
            m.put("tokensIn", (long) m.get("tokensIn") + s.getTokensIn());
            m.put("tokensOut", (long) m.get("tokensOut") + s.getTokensOut());
            m.put("cost", Math.round(((double) m.get("cost") + s.getCost()) * 10000.0) / 10000.0);
        }

        return Result.ok(List.copyOf(aggregated.values()));
    }

    /**
     * Get usage aggregated by user.
     */
    @GetMapping("/by-user")
    public Result<List<Map<String, Object>>> getUsageByUser(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<LlmUsageStat> stats = usageService.getUsageByUser(startDate, endDate);

        // Aggregate by userId
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (LlmUsageStat s : stats) {
            aggregated.computeIfAbsent(s.getUserId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", k);
                m.put("requestCount", 0);
                m.put("tokensIn", 0L);
                m.put("tokensOut", 0L);
                m.put("cost", 0.0);
                return m;
            });
            Map<String, Object> m = aggregated.get(s.getUserId());
            m.put("requestCount", (int) m.get("requestCount") + s.getRequestCount());
            m.put("tokensIn", (long) m.get("tokensIn") + s.getTokensIn());
            m.put("tokensOut", (long) m.get("tokensOut") + s.getTokensOut());
            m.put("cost", Math.round(((double) m.get("cost") + s.getCost()) * 10000.0) / 10000.0);
        }

        return Result.ok(List.copyOf(aggregated.values()));
    }

    /**
     * Get daily usage trend.
     */
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> getDailyUsage(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<LlmUsageStat> stats = usageService.getDailyUsage(startDate, endDate);

        // Aggregate by date
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (LlmUsageStat s : stats) {
            aggregated.computeIfAbsent(s.getStatDate(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", k);
                m.put("requestCount", 0);
                m.put("tokensIn", 0L);
                m.put("tokensOut", 0L);
                m.put("cost", 0.0);
                return m;
            });
            Map<String, Object> m = aggregated.get(s.getStatDate());
            m.put("requestCount", (int) m.get("requestCount") + s.getRequestCount());
            m.put("tokensIn", (long) m.get("tokensIn") + s.getTokensIn());
            m.put("tokensOut", (long) m.get("tokensOut") + s.getTokensOut());
            m.put("cost", Math.round(((double) m.get("cost") + s.getCost()) * 10000.0) / 10000.0);
        }

        return Result.ok(List.copyOf(aggregated.values()));
    }
}
