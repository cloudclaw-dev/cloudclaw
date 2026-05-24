package run.cloudclaw.agent.prompt;

import run.cloudclaw.common.model.PromptLog;
import run.cloudclaw.common.repository.PromptLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromptLogService")
class PromptLogServiceTest {

    @Mock private PromptLogRepository repository;

    @InjectMocks
    private PromptLogService service;

    @Test
    @DisplayName("query 按 sessionId 查询")
    void query_bySessionId() {
        Page<PromptLog> page = new PageImpl<>(List.of(new PromptLog()));
        when(repository.findBySessionIdAndCreatedAtBetween(
                eq("s-1"), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query("s-1", null, null, null, null, 1, 20);
        assertEquals(1, result.getTotalElements());
        verify(repository).findBySessionIdAndCreatedAtBetween(
                eq("s-1"), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("query 按 agentId 查询")
    void query_byAgentId() {
        Page<PromptLog> page = new PageImpl<>(List.of());
        when(repository.findByAgentIdAndCreatedAtBetween(
                eq("a-1"), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query(null, "a-1", null, null, null, 1, 20);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("query 按 sessionId + agentId 查询")
    void query_bySessionAndAgent() {
        Page<PromptLog> page = new PageImpl<>(List.of());
        when(repository.findBySessionIdAndAgentIdAndCreatedAtBetween(
                eq("s-1"), eq("a-1"), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query("s-1", "a-1", null, null, null, 1, 20);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("query 按关键词搜索")
    void query_byKeyword() {
        Page<PromptLog> page = new PageImpl<>(List.of());
        when(repository.searchByKeyword(
                eq("test"), any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query(null, null, null, null, "test", 1, 20);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("query 无过滤条件应查全部")
    void query_all() {
        Page<PromptLog> page = new PageImpl<>(List.of());
        when(repository.findByCreatedAtBetween(
                any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query(null, null, null, null, null, 1, 20);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    @DisplayName("query 空关键词应视为无关键词")
    void query_blankKeyword() {
        Page<PromptLog> page = new PageImpl<>(List.of());
        when(repository.findByCreatedAtBetween(
                any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.query(null, null, null, null, "  ", 1, 20);
        assertEquals(0, result.getTotalElements());
        verify(repository).findByCreatedAtBetween(
                any(LocalDateTime.class), any(LocalDateTime.class), any(PageRequest.class));
    }
}
