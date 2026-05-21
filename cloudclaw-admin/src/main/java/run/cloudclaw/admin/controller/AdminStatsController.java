package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.repository.AdminAgentRepository;
import run.cloudclaw.admin.repository.AdminMessageRepository;
import run.cloudclaw.admin.repository.AdminSessionRepository;
import run.cloudclaw.admin.repository.AdminUserRepository;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.llm.model.LlmUsageStat;
import run.cloudclaw.llm.service.LlmUsageService;
import run.cloudclaw.common.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminUserRepository userRepository;
    private final AdminSessionRepository sessionRepository;
    private final AdminMessageRepository messageRepository;
    private final AdminAgentRepository agentRepository;
    private final LlmUsageService llmUsageService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get usage statistics with LLM token usage and daily trends.
     */
    @GetMapping("/usage")
    public Result<Map<String, Object>> getUsageStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.debug("Admin fetching usage statistics");

        String sd = startDate != null ? startDate : LocalDate.now().minusDays(30).format(DATE_FMT);
        String ed = endDate != null ? endDate : LocalDate.now().format(DATE_FMT);

        // Basic counts
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userRepository.count());
        stats.put("sessionCount", sessionRepository.count());
        stats.put("messageCount", messageRepository.count());
        stats.put("agentCount", agentRepository.count());

        // LLM usage summary
        Map<String, Object> llmSummary = llmUsageService.getUsageSummary(sd, ed);
        stats.put("totalTokensIn", llmSummary.get("totalTokensIn"));
        stats.put("totalTokensOut", llmSummary.get("totalTokensOut"));
        stats.put("totalTokens", (long) llmSummary.get("totalTokensIn") + (long) llmSummary.get("totalTokensOut"));
        stats.put("totalRequests", llmSummary.get("totalRequests"));
        stats.put("totalCost", llmSummary.get("totalCost"));

        // Daily token usage trend
        List<LlmUsageStat> dailyStats = llmUsageService.getDailyUsage(sd, ed);
        Map<String, Map<String, Object>> dailyAgg = new LinkedHashMap<>();
        for (LlmUsageStat s : dailyStats) {
            dailyAgg.computeIfAbsent(s.getStatDate(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", k);
                m.put("tokensIn", 0L);
                m.put("tokensOut", 0L);
                m.put("tokens", 0L);
                m.put("requests", 0);
                m.put("cost", 0.0);
                return m;
            });
            Map<String, Object> m = dailyAgg.get(s.getStatDate());
            m.put("tokensIn", (long) m.get("tokensIn") + s.getTokensIn());
            m.put("tokensOut", (long) m.get("tokensOut") + s.getTokensOut());
            m.put("tokens", (long) m.get("tokens") + s.getTokensIn() + s.getTokensOut());
            m.put("requests", (int) m.get("requests") + s.getRequestCount());
            m.put("cost", Math.round(((double) m.get("cost") + s.getCost()) * 10000.0) / 10000.0);
        }
        stats.put("daily", List.copyOf(dailyAgg.values()));

        return Result.ok(stats);
    }

    /**
     * Get session statistics with daily trends.
     */
    @GetMapping("/sessions")
    public Result<Map<String, Object>> getSessionStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.debug("Admin fetching session statistics");

        long totalSessions = sessionRepository.count();

        LocalDateTime activeThreshold = LocalDateTime.now().minusMinutes(30);
        long activeSessions = sessionRepository.findAll().stream()
                .filter(session -> session.getUpdatedAt() != null
                        && session.getUpdatedAt().isAfter(activeThreshold))
                .count();

        // Build daily session trend from sessions table
        String sd = startDate != null ? startDate : LocalDate.now().minusDays(30).format(DATE_FMT);
        String ed = endDate != null ? endDate : LocalDate.now().format(DATE_FMT);

        LocalDate start = LocalDate.parse(sd, DATE_FMT);
        LocalDate end = LocalDate.parse(ed, DATE_FMT);

        // Initialize all dates with 0
        Map<String, Integer> dailyCount = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dailyCount.put(d.format(DATE_FMT), 0);
        }

        // Count sessions per day
        List<Session> sessions = sessionRepository.findAll();
        DateTimeFormatter dateTimeDayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Session s : sessions) {
            if (s.getCreatedAt() != null) {
                String day = s.getCreatedAt().format(dateTimeDayFmt);
                dailyCount.merge(day, 1, Integer::sum);
            }
        }

        // Convert to list of maps
        List<Map<String, Object>> daily = dailyCount.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", totalSessions);
        stats.put("activeSessions", activeSessions);
        stats.put("daily", daily);

        return Result.ok(stats);
    }
}
