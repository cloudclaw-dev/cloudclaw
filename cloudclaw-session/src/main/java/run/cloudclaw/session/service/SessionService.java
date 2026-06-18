package run.cloudclaw.session.service;

import run.cloudclaw.common.dto.MessageVo;
import run.cloudclaw.common.dto.PageResult;
import run.cloudclaw.common.dto.PollResult;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.session.cache.SessionCache;
import run.cloudclaw.session.repository.MessageRepository;
import run.cloudclaw.common.event.SessionDeleteEvent;
import run.cloudclaw.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service layer for session management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final SessionItemRepository sessionItemRepository;
    private final SessionCache sessionCache;
    private final ApplicationEventPublisher eventPublisher;

    // Fix M8: Per-session locks to prevent concurrent cache update race conditions
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    private ReentrantLock getLock(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
    }

    @Transactional
    public Session createSession(String userId, String agentId, String title) {
        Session session = new Session();
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setTitle(title);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        Session saved = sessionRepository.save(session);
        log.info("Created session {} for user {}", saved.getId(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResult<Session> listSessions(String userId, int page, int size) {
        return listSessions(userId, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<Session> listSessions(String userId, String agentId, int page, int size) {
        Page<Session> sessionPage;
        if (agentId != null && !agentId.isEmpty()) {
            sessionPage = sessionRepository.findByUserIdAndAgentIdOrderByUpdatedAtDesc(
                    userId, agentId, PageRequest.of(page - 1, size));
        } else {
            sessionPage = sessionRepository.findByUserIdOrderByUpdatedAtDesc(
                    userId, PageRequest.of(page - 1, size));
        }
        log.debug("Listed sessions for user {} (agentId={}): {} results on page {}", userId, agentId, sessionPage.getNumberOfElements(), page);
        return PageResult.of(
                sessionPage.getContent(),
                sessionPage.getTotalElements(),
                sessionPage.getNumber(),
                sessionPage.getSize()
        );
    }

    @Transactional(readOnly = true)
    public Session getSession(String userId, String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, sessionId));

        if (!session.getUserId().equals(userId)) {
            log.warn("User {} attempted to access session {} owned by {}", userId, sessionId, session.getUserId());
            throw new BusinessException(ErrorCode.SESSION_ACCESS_DENIED, sessionId);
        }

        return session;
    }

    @Transactional
    public void deleteSession(String userId, String sessionId) {
        Session session = getSession(userId, sessionId);
        UUID sessionUuid = UUID.fromString(sessionId);
        // Delete session context items
        sessionItemRepository.deleteBySessionId(sessionId);
        // Batch delete messages instead of loading all into memory
        messageRepository.deleteBySessionId(sessionUuid);
        sessionRepository.delete(session);
        try { sessionCache.evictContext(sessionId); sessionCache.evictSession(sessionId); } catch (Exception e) { log.debug("Cache eviction failed (non-critical): {}", e.getMessage()); }
        // Fix M8: Clean up per-session lock on delete to prevent memory leak
        sessionLocks.remove(sessionId);
        eventPublisher.publishEvent(new SessionDeleteEvent(sessionId));
        log.info("Deleted session {} for user {}", sessionId, userId);
    }

    @Transactional(readOnly = true)
    public List<Message> loadContext(String sessionId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
        try {
            if (!messages.isEmpty()) {
                sessionCache.saveContext(sessionId, messages);
            }
        } catch (Exception e) {
            log.debug("Cache save failed (non-critical): {}", e.getMessage());
        }

        log.debug("Loaded context for session {} from DB: {} messages", sessionId, messages.size());
        return messages;
    }

    /**
     * Fix M8: Save message and refresh cache atomically per session to prevent race conditions.
     * Uses a per-session ReentrantLock so that concurrent saves to the same session
     * serialize their DB save + cache refresh, avoiding stale cache reads.
     *
     * Cache refresh is deferred to after transaction commit to avoid reading uncommitted data.
     */
    @Transactional
    public Message saveMessage(Message message) {
        String sessionId = message.getSessionId().toString();
        ReentrantLock lock = getLock(sessionId);
        lock.lock();
        try {
            Message saved = messageRepository.save(message);
            log.debug("Saved message {} for session {}", saved.getId(), saved.getSessionId());

            sessionRepository.updateTimestamp(saved.getSessionId().toString(), LocalDateTime.now());

            // Register cache refresh to execute after transaction commit
            final String savedSessionId = saved.getSessionId().toString();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshContextCache(savedSessionId);
                }
            });

            return saved;
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public PageResult<Message> getMessages(String sessionId, int page, int size) {
        UUID sessionUuid = UUID.fromString(sessionId);
        Page<Message> messagePage = messageRepository.findBySessionIdOrderByCreatedAtAsc(
                sessionUuid, PageRequest.of(page - 1, size));
        return PageResult.of(
                messagePage.getContent(),
                messagePage.getTotalElements(),
                messagePage.getNumber(),
                messagePage.getSize()
        );
    }

    /**
     * Fix H6: Update title with userId validation to prevent cross-user modification.
     */
    @Transactional
    public void updateTitle(String sessionId, String userId, String title) {
        Session session = getSession(userId, sessionId);
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Updated title for session {} by user {}", sessionId, userId);
    }

    /**
     * Fix M8: Refresh the context cache for a session.
     * Should be called under the per-session lock to ensure atomicity with message saves.
     */
    private void refreshContextCache(String sessionId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
        try { sessionCache.saveContext(sessionId, messages); } catch (Exception e) { log.debug("Cache save failed (non-critical): {}", e.getMessage()); }
    }

    @Transactional(readOnly = true)
    public PollResult pollMessages(String sessionId, String afterMessageId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        List<Message> messages;

        if (afterMessageId != null && !afterMessageId.isBlank()) {
            Message afterMsg = messageRepository.findById(UUID.fromString(afterMessageId)).orElse(null);
            if (afterMsg != null) {
                messages = messageRepository.findBySessionIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        sessionUuid, afterMsg.getCreatedAt());
            } else {
                messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
            }
        } else {
            messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
            if (messages.size() > 50) {
                messages = messages.subList(messages.size() - 50, messages.size());
            }
        }

        List<MessageVo> result = messages.stream()
                .map(m -> new MessageVo(m.getId(), m.getRole(), m.getContent(),
                        m.getStatus() != null ? m.getStatus() : "completed", m.getCreatedAt()))
                .toList();

        return new PollResult(result, messages.size() >= 100);
    }

    @Transactional
    public void updateLastActiveAt(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    @Transactional(readOnly = true)
    public Message findMessageByRequestId(String requestId) {
        return messageRepository.findByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public Message findMessageById(UUID messageId) {
        return messageRepository.findById(messageId).orElse(null);
    }

    /**
     * Fix H6: Update session status with userId validation to prevent cross-user modification.
     */
    @Transactional
    public void updateSessionStatus(String sessionId, String userId, String status) {
        Session session = getSession(userId, sessionId);
        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    /**
     * Fix H6: Update active agent path with userId validation to prevent cross-user modification.
     */
    @Transactional
    public void updateActiveAgentPath(String sessionId, String userId, String activeAgentPath) {
        Session session = getSession(userId, sessionId);
        session.setActiveAgentPath(activeAgentPath);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Updated active_agent_path for session {} by user {}: {}", sessionId, userId, activeAgentPath);
    }

    @Transactional(readOnly = true)
    public Session findSessionById(String sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * Fix H6: Update workflow state with userId validation to prevent cross-user modification.
     */
    @Transactional
    public void updateWorkflowState(String sessionId, String userId, String workflowState) {
        Session session = getSession(userId, sessionId);
        session.setWorkflowState(workflowState);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Updated workflow_state for session {} by user {}: {} chars", sessionId, userId,
                workflowState != null ? workflowState.length() : 0);
    }
}
