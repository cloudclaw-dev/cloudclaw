package run.cloudclaw.admin.repository;

import run.cloudclaw.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Admin repository for User entity access.
 * Provides standard CRUD operations for user management.
 */
public interface AdminUserRepository extends JpaRepository<User, UUID> {
    boolean existsByUsername(String username);
}
