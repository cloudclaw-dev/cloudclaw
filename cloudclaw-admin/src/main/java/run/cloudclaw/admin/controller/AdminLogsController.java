package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.logging.InMemoryLogAppender;
import run.cloudclaw.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for viewing recent application logs.
 *
 * <p>Reads from the in-memory ring buffer maintained by
 * {@link InMemoryLogAppender}. Only accessible by ADMIN users.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogsController {

    private final InMemoryLogAppender logAppender;

    /**
     * Get recent log entries.
     *
     * @param level optional level filter (error, warn, info, debug, trace)
     * @param limit max number of entries to return (default 200, max 500)
     * @return list of log entries (newest first)
     */
    @GetMapping
    public Result<List<Map<String, String>>> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "200") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<Map<String, String>> logs = logAppender.getLogs(level, safeLimit);
        return Result.ok(logs);
    }
}
