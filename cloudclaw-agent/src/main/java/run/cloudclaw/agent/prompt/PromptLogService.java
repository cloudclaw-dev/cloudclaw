package run.cloudclaw.agent.prompt;

import run.cloudclaw.common.model.PromptLog;
import run.cloudclaw.common.repository.PromptLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptLogService {

    private final PromptLogRepository repository;

    private static final int MAX_PROMPT_LOGS = 1000;
    private static final int CLEANUP_INTERVAL = 50;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    @Async("asyncTaskExecutor")
    public void logAsync(String sessionId, String agentId, String userId,
                         String modelId, String role, String content,
                         Integer tokenCount, String toolCalls, Integer durationMs) {
        try {
            PromptLog promptLog = PromptLog.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .agentId(agentId)
                    .userId(userId)
                    .modelId(modelId)
                    .role(role)
                    .content(content)
                    .tokenCount(tokenCount)
                    .toolCalls(toolCalls)
                    .durationMs(durationMs)
                    .createdAt(LocalDateTime.now())
                    .build();
            repository.save(promptLog);
            if (callCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
                cleanupOldLogs();
            }
        } catch (Exception e) {
            log.warn("Failed to save prompt log: {}", e.getMessage());
        }
    }

    private synchronized void cleanupOldLogs() {
        try {
            long count = repository.count();
            if (count > MAX_PROMPT_LOGS) {
                // Delete oldest logs exceeding the limit
                Page<PromptLog> oldest = repository.findAll(
                        PageRequest.of(0, (int)(count - MAX_PROMPT_LOGS),
                                Sort.by(Sort.Direction.ASC, "createdAt")));
                repository.deleteAllById(oldest.getContent().stream()
                        .map(PromptLog::getId).toList());
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old prompt logs: {}", e.getMessage());
        }
    }

    public Page<PromptLog> query(String sessionId, String agentId,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  String keyword, int page, int size) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.of(2099, 12, 31, 23, 59);
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (keyword != null && !keyword.isBlank()) {
            return repository.searchByKeyword(keyword, start, end, pageable);
        }
        if (sessionId != null && agentId != null) {
            return repository.findBySessionIdAndAgentIdAndCreatedAtBetween(sessionId, agentId, start, end, pageable);
        }
        if (sessionId != null) {
            return repository.findBySessionIdAndCreatedAtBetween(sessionId, start, end, pageable);
        }
        if (agentId != null) {
            return repository.findByAgentIdAndCreatedAtBetween(agentId, start, end, pageable);
        }
        return repository.findByCreatedAtBetween(start, end, pageable);
    }
}
