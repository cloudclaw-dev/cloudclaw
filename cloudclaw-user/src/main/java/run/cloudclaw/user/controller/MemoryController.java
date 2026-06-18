package run.cloudclaw.user.controller;

import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for memory management.
 *
 * <p>Two types of memory, all scoped to the authenticated user:</p>
 * <ul>
 *   <li><b>Profile</b> — user traits, habits, preferences (persistent)</li>
 *   <li><b>Session</b> — conversation-scoped context (cleared when session ends)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    // ==================== Profile ====================

    @GetMapping("/profile")
    public Result<List<ProfileItem>> listProfile(@AuthUser String userId) {
        log.debug("User [{}] listing profile", userId);
        return Result.ok(memoryService.getProfileItems(userId));
    }

    @PutMapping("/profile")
    public Result<List<ProfileItem>> replaceProfile(@AuthUser String userId,
                                                     @RequestBody Map<String, String> request) {
        log.info("User [{}] replacing profile", userId);
        String profileText = request.getOrDefault("profile", "");
        return Result.ok(memoryService.replaceProfileItems(userId, profileText));
    }

    @PostMapping("/profile")
    public Result<ProfileItem> addProfile(@AuthUser String userId,
                                          @RequestBody Map<String, String> request) {
        log.info("User [{}] adding profile item", userId);
        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return Result.error(400, "Content is required");
        }
        return Result.ok(memoryService.addProfileItem(userId, content));
    }

    @PutMapping("/profile/{itemId}")
    public Result<ProfileItem> updateProfileItem(@AuthUser String userId,
                                                   @PathVariable String itemId,
                                                   @RequestBody Map<String, String> request) {
        log.info("User [{}] updating profile item: {}", userId, itemId);
        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return Result.error(400, "Content is required");
        }
        return Result.ok(memoryService.updateProfileItem(userId, itemId, content));
    }

    @DeleteMapping("/profile/{itemId}")
    public Result<Void> deleteProfile(@AuthUser String userId, @PathVariable String itemId) {
        log.info("User [{}] deleting profile item: {}", userId, itemId);
        memoryService.deleteProfileItem(userId, itemId);
        return Result.ok(null);
    }

    // ==================== Session ====================

    @GetMapping("/sessions")
    public Result<List<SessionItem>> listSessions(@AuthUser String userId,
                                                   @RequestParam(required = false) String sessionId) {
        log.debug("User [{}] listing session context, sessionId={}", userId, sessionId);
        List<SessionItem> items;
        if (sessionId != null && !sessionId.isBlank()) {
            items = memoryService.getSessionItems(sessionId);
        } else {
            items = memoryService.getSessionItemsByUser(userId);
        }
        return Result.ok(items);
    }

    @DeleteMapping("/sessions/{itemId}")
    public Result<Void> deleteSession(@AuthUser String userId, @PathVariable String itemId) {
        log.info("User [{}] deleting session item: {}", userId, itemId);
        memoryService.deleteSessionItem(userId, itemId);
        return Result.ok(null);
    }
}
