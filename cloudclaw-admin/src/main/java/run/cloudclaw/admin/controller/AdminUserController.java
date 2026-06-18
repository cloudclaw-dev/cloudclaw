package run.cloudclaw.admin.controller;

import run.cloudclaw.admin.dto.CreateUserRequest;
import run.cloudclaw.admin.dto.UpdateUserRequest;
import run.cloudclaw.admin.repository.AdminUserRepository;
import run.cloudclaw.common.dto.PageResult;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.dto.UserDTO;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Create a new user.
     *
     * @param request the user creation request
     * @return the created user DTO
     */
    @PostMapping
    public Result<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Admin creating user with username: {}", request.getUsername());

        // Check username uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.CONFLICT, request.getUsername());
        }

        // Fix M3: Check email uniqueness at business layer
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already in use: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole(User.UserRole.valueOf(request.getRole()));

        User saved = userRepository.save(user);
        log.info("User created successfully with id: {}", saved.getId());

        return Result.ok(toDTO(saved));
    }

    /**
     * List all users with pagination.
     *
     * @param page page number (1-based)
     * @param size page size
     * @return paginated user list
     */
    @GetMapping
    public Result<PageResult<UserDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Fix M7: Cap page size at 100 to prevent excessive DB load
        size = Math.min(size, 100);
        log.debug("Admin listing users, page: {}, size: {}", page, size);

        Page<User> userPage = userRepository.findAll(
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        java.util.List<UserDTO> dtos = userPage.getContent().stream()
                .map(this::toDTO)
                .toList();

        return Result.ok(PageResult.of(dtos, userPage.getTotalElements(), page, size));
    }

    /**
     * Update an existing user.
     *
     * @param id      the user ID
     * @param request the update request
     * @return the updated user DTO
     */
    @PutMapping("/{id}")
    public Result<UserDTO> updateUser(@PathVariable String id,
                                      @Valid @RequestBody UpdateUserRequest request) {
        log.info("Admin updating user with id: {}", id);

        UUID userId = UUID.fromString(id);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, id));

        if (request.getEmail() != null) {
            // Fix M3: Check email uniqueness when updating
            if (userRepository.existsByEmailAndIdNot(request.getEmail(), userId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Email already in use: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getRole() != null) {
            user.setRole(User.UserRole.valueOf(request.getRole()));
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully: {}", id);

        return Result.ok(toDTO(saved));
    }

    /**
     * Delete a user by ID.
     *
     * @param id the user ID
     * @return empty result on success
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable String id) {
        log.info("Admin deleting user with id: {}", id);

        UUID userId = UUID.fromString(id);
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, id);
        }

        userRepository.deleteById(userId);
        log.info("User deleted successfully: {}", id);

        return Result.ok();
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
