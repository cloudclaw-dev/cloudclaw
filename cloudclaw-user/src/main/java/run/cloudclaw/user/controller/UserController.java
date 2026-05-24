package run.cloudclaw.user.controller;

import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.dto.UserDTO;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.User;
import run.cloudclaw.memory.service.MemoryService;
import run.cloudclaw.user.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for current user information and preferences.
 *
 * <p>Provides endpoints for the authenticated user to view their
 * profile and update preferences. All operations are inherently
 * scoped to the authenticated user via the JWT token.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserQueryRepository userQueryRepository;
    private final MemoryService memoryService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Get the current authenticated user's profile information.
     *
     * <p>Returns user details without the password field for security.</p>
     *
     * @param userId the authenticated user ID, injected from JWT
     * @return the user's profile as a DTO (without password)
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@AuthUser String userId) {
        log.debug("Getting current user info for [{}]", userId);
        User user = userQueryRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        UserDTO dto = new UserDTO();
        dto.setId(user.getId().toString());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        dto.setEnabled(user.getEnabled());
        dto.setCreatedAt(user.getCreatedAt());

        return Result.ok(dto);
    }

    /**
     * Update the current user's preferences.
     *
     * <p>Accepts a map of preference key-value pairs to update.
     * The preferences are stored in the user memory system.</p>
     *
     * @param userId      the authenticated user ID, injected from JWT
     * @param preferences map of preference keys to values
     * @return empty result on success
     */
    @PutMapping("/me/preferences")
    public Result<Void> updatePreferences(@AuthUser String userId,
                                           @RequestBody Map<String, Object> preferences) {
        log.info("User [{}] updating preferences: {}", userId, preferences.keySet());
        // Store preferences as profile items
        try {
            for (Map.Entry<String, Object> entry : preferences.entrySet()) {
                String content = entry.getKey() + ": " + entry.getValue();
                memoryService.addProfileItem(userId, content);
            }
        } catch (Exception e) {
            log.warn("Failed to store preferences for user [{}]: {}", userId, e.getMessage());
        }
        return Result.ok();
    }

    /**
     * Change the current user's password.
     *
     * @param userId   the authenticated user ID, injected from JWT
     * @param request  map containing "oldPassword" and "newPassword"
     * @return empty result on success
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@AuthUser String userId,
                                        @RequestBody Map<String, String> request) {
        log.info("User [{}] changing password", userId);

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "oldPassword and newPassword are required");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must be at least 6 characters");
        }

        User user = userQueryRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userQueryRepository.save(user);

        log.info("User [{}] password changed successfully", userId);
        return Result.ok();
    }
}
