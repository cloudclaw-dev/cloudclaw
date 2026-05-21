package run.cloudclaw.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtil {

    private SecurityUtil() {
        // utility class
    }

    /**
     * Extract the current authenticated user ID from the SecurityContextHolder.
     *
     * @return the current user ID as a UUID
     * @throws IllegalStateException if no authenticated user is found
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String) {
            return UUID.fromString((String) principal);
        }
        throw new IllegalStateException("Cannot extract user ID from authentication principal");
    }
}
