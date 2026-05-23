package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Admin repository for Message entity access.
 * Provides standard CRUD operations for message statistics.
 */
public interface AdminMessageRepository extends JpaRepository<Message, UUID> {
}
