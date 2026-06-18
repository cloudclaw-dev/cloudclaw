package run.cloudclaw.session.repository;

import run.cloudclaw.common.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Message entity operations.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Find all messages for a given session, ordered by creation time ascending.
     *
     * @param sessionId the session ID to filter by
     * @return list of messages in chronological order
     */
    List<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /**
     * Find all messages for a given session with pagination, ordered by creation time ascending.
     *
     * @param sessionId the session ID to filter by
     * @param pageable  pagination information
     * @return paginated list of messages in chronological order
     */
    Page<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId, Pageable pageable);

    /**
     * Count messages for a given session.
     *
     * @param sessionId the session ID to filter by
     * @return the number of messages in the session
     */
    long countBySessionId(UUID sessionId);

    /**
     * Find messages after a given creation time, ordered ascending.
     * Used for polling incremental messages.
     */
    List<Message> findBySessionIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID sessionId, java.time.LocalDateTime after);

    /**
     * Find by requestId for idempotency checks.
     */
    Message findByRequestId(String requestId);

    /**
     * Delete all messages for a given session (batch operation).
     */
    void deleteBySessionId(UUID sessionId);
}
