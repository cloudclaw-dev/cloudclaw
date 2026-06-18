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
import org.springframework.core.env.Environment;
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
    private final Environment environment;

    @GetMapping("/info")
    public Result<?> getSystemInfo() {
        String[] profiles = environment.getActiveProfiles();
        String mode = (profiles.length > 0) ? profiles[0] : "standalone";
        String version = environment.getProperty("cloudclaw.version", "unknown");
        return Result.ok(java.util.Map.of(
            "version", version,
            "mode", mode
        ));
    }

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
        long activeSessions = sessionRepository.countByUpdatedAtAfter(activeThreshold);

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

        // Count sessions per day using DB aggregation
        LocalDateTime aggStart = start.atStartOfDay();
        LocalDateTime aggEnd = end.plusDays(1).atStartOfDay();
        List<Object[]> dailyAgg = sessionRepository.countByDateRange(aggStart, aggEnd);
        DateTimeFormatter dateTimeDayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Object[] row : dailyAgg) {
            String day = row[0].toString();
            if (day.length() > 10) day = day.substring(0, 10); // Handle full datetime strings
            day = LocalDate.parse(day, DateTimeFormatter.ISO_LOCAL_DATE).format(dateTimeDayFmt);
            Number count = (Number) row[1];
            dailyCount.merge(day, count.intValue(), Integer::sum);
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

    /**
     * Get recent sessions list with agent names.
     */
    @GetMapping("/recent-sessions")
    public Result<List<Map<String, Object>>> getRecentSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.min(size, 50),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));

        var sessions = sessionRepository.findAll(pageable);

        // Load agent names in batch
        Map<String, String> agentNames = new HashMap<>();
        for (var a : agentRepository.findAll()) {
            agentNames.put(a.getId().toString(), a.getName());
        }

        List<Map<String, Object>> list = sessions.getContent().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId().toString());
            m.put("title", s.getTitle() != null && !s.getTitle().isEmpty() ? s.getTitle() : null);
            m.put("agentName", s.getAgentId() != null ? agentNames.getOrDefault(s.getAgentId().toString(), "-") : "-");
            m.put("createdAt", s.getCreatedAt());
            m.put("lastActive", s.getUpdatedAt());
            return m;
        }).toList();

        return Result.ok(list);
    }
}
