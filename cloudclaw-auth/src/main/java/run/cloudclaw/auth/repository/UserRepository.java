package run.cloudclaw.auth.repository;

import run.cloudclaw.common.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity access in the auth module.
 *
 * <p>Extends JpaRepository to provide standard CRUD operations
 * plus custom finder by username.</p>
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their username.
     *
     * @param username the username to search for
     * @return an Optional containing the user if found
     */
    Optional<User> findByUsername(String username);
}
