package run.cloudclaw.debug.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import run.cloudclaw.debug.repository.ChatTraceRepository;

import java.util.List;
import java.util.Map;

/**
 * Debug API endpoints for querying and managing chat traces.
 * Only available when cloudclaw.debug.enabled=true.
 * Should be secured with ROLE_ADMIN in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/debug")
@ConditionalOnProperty(name = "cloudclaw.debug.enabled", havingValue = "true")
public class DebugTraceController {

    private final ChatTraceRepository traceRepository;

    public DebugTraceController(ChatTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    /**
     * Query traces with optional filters (paginated, no spans_json).
     */
    @GetMapping("/traces")
    public ResponseEntity<List<Map<String, Object>>> listTraces(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(traceRepository.queryTraces(sessionId, agentId, status, from, to, page, size));
    }

    /**
     * Get full trace detail by ID (including spans_json).
     */
    @GetMapping("/traces/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable String traceId) {
        Map<String, Object> trace = traceRepository.getTrace(traceId);
        if (trace == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(trace);
    }

    /**
     * Get aggregate statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(traceRepository.getStats(agentId, from, to));
    }

    /**
     * Clean up old trace data.
     */
    @DeleteMapping("/traces")
    public ResponseEntity<Map<String, Object>> cleanupTraces(
            @RequestParam String before) {
        int deleted = traceRepository.deleteBefore(before);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
