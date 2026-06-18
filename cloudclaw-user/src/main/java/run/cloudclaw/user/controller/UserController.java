package run.cloudclaw.user.controller;

import run.cloudclaw.auth.repository.ChannelBindingRepository;
import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.ChannelBindingDTO;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.dto.UpdateProfileRequest;
import run.cloudclaw.common.dto.UserDTO;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.ChannelBinding;
import run.cloudclaw.common.model.User;
import run.cloudclaw.memory.service.MemoryService;
import run.cloudclaw.user.repository.UserQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryRepository userQueryRepository;
    private final ChannelBindingRepository channelBindingRepository;
    private final MemoryService memoryService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@AuthUser String userId) {
        log.debug("Getting current user info for [{}]", userId);
        User user = userQueryRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        List<ChannelBinding> bindings = channelBindingRepository.findByUserId(userId);
        List<ChannelBindingDTO> bindingDTOs = bindings.stream()
                .map(this::toBindingDTO)
                .collect(Collectors.toList());

        return Result.ok(toUserDTO(user, bindingDTOs));
    }

    @PutMapping("/me/profile")
    public Result<UserDTO> updateProfile(@AuthUser String userId,
                                          @RequestBody UpdateProfileRequest request) {
        log.info("User [{}] updating profile", userId);
        User user = userQueryRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        user = userQueryRepository.save(user);

        List<ChannelBinding> bindings = channelBindingRepository.findByUserId(userId);
        return Result.ok(toUserDTO(user, bindings.stream().map(this::toBindingDTO).collect(Collectors.toList())));
    }

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
        if (!newPassword.matches("^(?=.*[a-zA-Z])(?=.*\\d).+$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password must contain at least one letter and one digit");
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

    @GetMapping("/me/bindings")
    public Result<List<ChannelBindingDTO>> getBindings(@AuthUser String userId) {
        List<ChannelBinding> bindings = channelBindingRepository.findByUserId(userId);
        return Result.ok(bindings.stream().map(this::toBindingDTO).collect(Collectors.toList()));
    }

    @DeleteMapping("/me/bindings/{channelType}")
    public Result<Void> unbind(@AuthUser String userId, @PathVariable String channelType) {
        log.info("User [{}] unbinding channel [{}]", userId, channelType);
        ChannelBinding binding = channelBindingRepository
                .findByUserIdAndChannelType(userId, channelType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_BINDING_NOT_FOUND,
                        "Binding not found for channel: " + channelType));
        channelBindingRepository.delete(binding);
        log.info("User [{}] unbound from channel [{}]", userId, channelType);
        return Result.ok();
    }

    @PutMapping("/me/preferences")
    public Result<Void> updatePreferences(@AuthUser String userId,
                                           @RequestBody Map<String, Object> preferences) {
        log.info("User [{}] updating preferences: {}", userId, preferences.keySet());
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

    private UserDTO toUserDTO(User user, List<ChannelBindingDTO> bindings) {
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
                .bindings(bindings)
                .build();
    }

    private ChannelBindingDTO toBindingDTO(ChannelBinding binding) {
        return ChannelBindingDTO.builder()
                .channelType(binding.getChannelType())
                .channelUserId(binding.getChannelUserId())
                .channelData(binding.getChannelData())
                .createdAt(binding.getCreatedAt() != null ? binding.getCreatedAt().toString() : null)
                .build();
    }
}
