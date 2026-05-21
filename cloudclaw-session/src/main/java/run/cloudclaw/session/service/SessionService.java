package run.cloudclaw.session.service;

import run.cloudclaw.common.dto.MessageVo;
import run.cloudclaw.common.dto.PageResult;
import run.cloudclaw.common.dto.PollResult;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.common.model.Session;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for session management operations.
 *
 * <p>Handles session CRUD, message storage, and Redis session caching.
 * All user-facing queries enforce user data isolation by filtering on userId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final SessionCache sessionCache;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create a new session.
     *
     * @param userId  the owner of the session
     * @param agentId the agent associated with the session
     * @param title   optional title for the session
     * @return the newly created session
     */
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

    /**
     * List sessions for a given user with pagination, ordered by most recently updated.
     *
     * @param userId the user ID to filter by (enforces data isolation)
     * @param page   page number (0-based)
     * @param size   page size
     * @return paginated result of sessions
     */
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

    /**
     * Get a session by ID, verifying that it belongs to the requesting user.
     *
     * @param userId    the requesting user ID (enforces data isolation)
     * @param sessionId the session ID
     * @return the session
     * @throws BusinessException if the session is not found or does not belong to the user
     */
    @Transactional(readOnly = true)
    public Session getSession(String userId, String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "Session not found: " + sessionId));

        if (!session.getUserId().equals(userId)) {
            log.warn("User {} attempted to access session {} owned by {}", userId, sessionId, session.getUserId());
            throw new BusinessException(403, "Access denied to session: " + sessionId);
        }

        return session;
    }

    /**
     * Delete a session, verifying ownership first.
     *
     * @param userId    the requesting user ID (enforces data isolation)
     * @param sessionId the session ID to delete
     * @throws BusinessException if the session is not found or does not belong to the user
     */
    @Transactional
    public void deleteSession(String userId, String sessionId) {
        Session session = getSession(userId, sessionId);
        UUID sessionUuid = UUID.fromString(sessionId);
        // Delete messages first (no cascade in entity)
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
        if (!messages.isEmpty()) {
            messageRepository.deleteAll(messages);
            log.debug("Deleted {} messages for session {}", messages.size(), sessionId);
        }
        sessionRepository.delete(session);
        try { sessionCache.evictContext(sessionId); sessionCache.evictSession(sessionId); } catch (Exception e) { log.debug("Cache eviction failed (non-critical): {}", e.getMessage()); }
        eventPublisher.publishEvent(new SessionDeleteEvent(sessionId));
        log.info("Deleted session {} for user {} ({} messages)", sessionId, userId, messages.size());
    }

    /**
     * Load the message context for a session. Tries Redis cache first;
     * on cache miss, loads from the database and caches the result.
     *
     * @param sessionId the session ID
     * @return the list of messages in the session, in chronological order
     */
    @Transactional(readOnly = true)
    public List<Message> loadContext(String sessionId) {
        // Load from DB directly
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
     * Save a message to the database and update the Redis cache.
     *
     * @param message the message to save
     * @return the saved message (with generated ID and timestamp)
     */
    @Transactional
    public Message saveMessage(Message message) {
        Message saved = messageRepository.save(message);
        log.debug("Saved message {} for session {}", saved.getId(), saved.getSessionId());

        // Update the session's updatedAt timestamp
        sessionRepository.findById(saved.getSessionId().toString()).ifPresent(session -> {
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });

        // Update the cache with the full context
        refreshContextCache(saved.getSessionId().toString());

        return saved;
    }

    /**
     * Get messages for a session with pagination.
     *
     * @param sessionId the session ID
     * @param page      page number (0-based)
     * @param size      page size
     * @return paginated result of messages
     */
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
     * Update the title of a session.
     *
     * @param sessionId the session ID
     * @param title     the new title
     */
    @Transactional
    public void updateTitle(String sessionId, String title) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "Session not found: " + sessionId));
        session.setTitle(title);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        log.info("Updated title for session {}", sessionId);
    }

    /**
     * Refresh the context cache for a session by loading all messages from DB
     * and saving them to Redis.
     *
     * @param sessionId the session ID
     */
    private void refreshContextCache(String sessionId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
        try { sessionCache.saveContext(sessionId, messages); } catch (Exception e) { log.debug("Cache save failed (non-critical): {}", e.getMessage()); }
    }

    /**
     * Poll for messages after a given message ID.
     *
     * @param sessionId     the session ID
     * @param afterMessageId return messages created after this message (null = return recent)
     * @return poll result with messages and hasMore flag
     */
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
            // No afterMessageId: return recent messages
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

    /**
     * Update lastActiveAt timestamp for a session.
     */
    @Transactional
    public void updateLastActiveAt(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * Find a message by its requestId (for idempotency).
     */
    @Transactional(readOnly = true)
    public Message findMessageByRequestId(String requestId) {
        return messageRepository.findByRequestId(requestId);
    }

    /**
     * Find a message by its ID.
     */
    @Transactional(readOnly = true)
    public Message findMessageById(UUID messageId) {
        return messageRepository.findById(messageId).orElse(null);
    }
}
