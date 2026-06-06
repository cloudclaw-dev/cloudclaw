package run.cloudclaw.debug.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import run.cloudclaw.debug.model.ChatSpan;
import run.cloudclaw.debug.model.ChatTrace;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Asynchronous persistence for ChatTrace records.
 */
@Slf4j
@Repository
public class ChatTraceRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChatTraceRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Async("debugTaskExecutor")
    public void saveAsync(ChatTrace trace) {
        try {
            String sql = "INSERT INTO chat_trace (trace_id, session_id, agent_id, user_id, model_id, " +
                         "start_time, end_time, duration_ms, input_tokens, output_tokens, total_tokens, " +
                         "tool_call_count, status, error_message, spans_json) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbc.update(sql,
                    trace.getTraceId(),
                    trace.getSessionId(),
                    trace.getAgentId(),
                    trace.getUserId(),
                    trace.getModelId(),
                    trace.getStartTime() != null ? Timestamp.from(trace.getStartTime()).toString() : null,
                    trace.getEndTime() != null ? Timestamp.from(trace.getEndTime()).toString() : null,
                    trace.getDurationMs(),
                    trace.getInputTokens(),
                    trace.getOutputTokens(),
                    trace.getTotalTokens(),
                    trace.getToolCallCount(),
                    trace.getStatus(),
                    trace.getErrorMessage(),
                    toJson(trace.getSpans()));
        } catch (Exception e) {
            log.warn("Failed to save trace {}: {}", trace.getTraceId(), e.getMessage());
        }
    }

    /**
     * Query traces with optional filters. Returns summary data (no spans_json).
     */
    public List<Map<String, Object>> queryTraces(String sessionId, String agentId, String status,
                                                   String from, String to, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT trace_id, session_id, agent_id, user_id, model_id, " +
                "start_time, end_time, duration_ms, input_tokens, output_tokens, total_tokens, " +
                "tool_call_count, status, error_message, created_at FROM chat_trace WHERE 1=1");

        if (sessionId != null && !sessionId.isBlank()) sql.append(" AND session_id = '").append(sessionId).append("'");
        if (agentId != null && !agentId.isBlank()) sql.append(" AND agent_id = '").append(agentId).append("'");
        if (status != null && !status.isBlank()) sql.append(" AND status = '").append(status).append("'");
        if (from != null && !from.isBlank()) sql.append(" AND start_time >= '").append(from).append("'");
        if (to != null && !to.isBlank()) sql.append(" AND start_time <= '").append(to).append("'");

        sql.append(" ORDER BY start_time DESC LIMIT ").append(size).append(" OFFSET ").append(page * size);

        return jdbc.queryForList(sql.toString());
    }

    /**
     * Get a single trace by ID, including spans_json.
     */
    public Map<String, Object> getTrace(String traceId) {
        String sql = "SELECT * FROM chat_trace WHERE trace_id = ?";
        List<Map<String, Object>> results = jdbc.queryForList(sql, traceId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Delete traces older than the given date.
     */
    public int deleteBefore(String before) {
        String sql = "DELETE FROM chat_trace WHERE start_time < ?";
        return jdbc.update(sql, before);
    }

    /**
     * Get aggregate statistics for traces.
     */
    public Map<String, Object> getStats(String agentId, String from, String to) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) as total, " +
                "COALESCE(AVG(duration_ms), 0) as avg_duration_ms, " +
                "COALESCE(AVG(input_tokens), 0) as avg_input_tokens, " +
                "COALESCE(AVG(output_tokens), 0) as avg_output_tokens, " +
                "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END), 0) as success_count, " +
                "COALESCE(SUM(CASE WHEN status='ERROR' THEN 1 ELSE 0 END), 0) as error_count, " +
                "COALESCE(SUM(tool_call_count), 0) as total_tool_calls " +
                "FROM chat_trace WHERE 1=1");

        if (agentId != null && !agentId.isBlank()) sql.append(" AND agent_id = '").append(agentId).append("'");
        if (from != null && !from.isBlank()) sql.append(" AND start_time >= '").append(from).append("'");
        if (to != null && !to.isBlank()) sql.append(" AND start_time <= '").append(to).append("'");

        return jdbc.queryForMap(sql.toString());
    }

    private String toJson(List<ChatSpan> spans) {
        if (spans == null || spans.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(Map.of("spans", spans));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize spans: {}", e.getMessage());
            return "[]";
        }
    }
}
