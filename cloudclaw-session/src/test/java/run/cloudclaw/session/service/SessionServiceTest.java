package run.cloudclaw.session.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.session.cache.SessionCache;
import run.cloudclaw.session.repository.MessageRepository;
import run.cloudclaw.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService")
class SessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private run.cloudclaw.common.repository.SessionItemRepository sessionItemRepository;
    @Mock private SessionCache sessionCache;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository, messageRepository, sessionItemRepository, sessionCache, eventPublisher);
    }

    @Test
    @DisplayName("createSession 应正确创建会话")
    void createSession() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("new-id");
            return s;
        });

        Session result = service.createSession("user-1", "agent-1", "Test Session");

        assertNotNull(result);
        assertEquals("user-1", result.getUserId());
        assertEquals("agent-1", result.getAgentId());
        assertEquals("Test Session", result.getTitle());
        assertNotNull(result.getCreatedAt());
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("getSession 不存在应抛 404")
    void getSession_notFound() {
        when(sessionRepository.findById("nonexistent")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSession("user-1", "nonexistent"));
        assertEquals(5001, ex.getCode());
    }

    @Test
    @DisplayName("getSession 非所有者应抛 403")
    void getSession_accessDenied() {
        Session session = new Session();
        session.setUserId("other-user");
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getSession("user-1", "s-1"));
        // Fix M4: Updated to match new SESSION_ACCESS_DENIED code (was 5002, now 5003)
        assertEquals(5003, ex.getCode());
    }

    @Test
    @DisplayName("getSession 所有者应返回 session")
    void getSession_owner() {
        Session session = new Session();
        session.setUserId("user-1");
        session.setId("s-1");
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));

        Session result = service.getSession("user-1", "s-1");
        assertEquals("s-1", result.getId());
    }

    @Test
    @DisplayName("deleteSession 应删除消息和会话")
    void deleteSession() {
        UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Session session = new Session();
        session.setUserId("user-1");
        session.setId(sessionId.toString());
        when(sessionRepository.findById(sessionId.toString())).thenReturn(Optional.of(session));

        service.deleteSession("user-1", sessionId.toString());

        verify(messageRepository).deleteBySessionId(sessionId);

        verify(sessionRepository).delete(session);
        verify(eventPublisher).publishEvent(any(run.cloudclaw.common.event.SessionDeleteEvent.class));
    }

    @Test
    @DisplayName("listSessions 应按用户查询")
    void listSessions() {
        Page<Session> page = new PageImpl<>(List.of(new Session()));
        when(sessionRepository.findByUserIdOrderByUpdatedAtDesc(eq("user-1"), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.listSessions("user-1", 1, 20);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("listSessions 带 agentId 应按用户+Agent 查询")
    void listSessions_withAgentId() {
        Page<Session> page = new PageImpl<>(List.of());
        when(sessionRepository.findByUserIdAndAgentIdOrderByUpdatedAtDesc(
                eq("user-1"), eq("agent-1"), any(PageRequest.class)))
                .thenReturn(page);

        var result = service.listSessions("user-1", "agent-1", 1, 20);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("updateTitle 应更新标题并校验 userId")
    void updateTitle() {
        Session session = new Session();
        session.setId("s-1");
        session.setUserId("user-1");
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        service.updateTitle("s-1", "user-1", "New Title");
        assertEquals("New Title", session.getTitle());
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("saveMessage 应保存并刷新缓存")
    void saveMessage() {
        Message msg = new Message();
        msg.setSessionId(UUID.randomUUID());
        when(messageRepository.save(any(Message.class))).thenReturn(msg);
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenReturn(List.of(msg));

        Message saved = service.saveMessage(msg);
        assertNotNull(saved);
        verify(sessionRepository).updateTimestamp(anyString(), any(LocalDateTime.class));
    }
}
