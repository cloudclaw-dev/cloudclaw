package run.cloudclaw.user.repository;

import run.cloudclaw.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for querying User entities within the user module.
 *
 * <p>This repository is maintained locally in the user module to avoid
 * circular dependencies with the auth module's UserRepository. It provides
 * read-only access to user data needed by user-facing API endpoints.</p>
 */
public interface UserQueryRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their username.
     *
     * @param username the username to search for
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);
}
