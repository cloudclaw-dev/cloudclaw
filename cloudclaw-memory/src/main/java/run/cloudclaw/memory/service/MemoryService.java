package run.cloudclaw.memory.service;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;
import run.cloudclaw.common.repository.ProfileItemRepository;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for memory operations.
 * Provides API for managing profile items and session items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final ProfileItemRepository profileItemRepository;
    private final SessionItemRepository sessionItemRepository;

    // ==================== Profile Items ====================

    @Transactional(readOnly = true)
    public List<ProfileItem> getProfileItems(String userId) {
        log.debug("Getting profile items for user: {}", userId);
        return profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public ProfileItem addProfileItem(String userId, String content) {
        log.info("Adding profile item for user: {}", userId);
        ProfileItem item = ProfileItem.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        return profileItemRepository.save(item);
    }

    @Transactional
    public ProfileItem updateProfileItem(String userId, String itemId, String content) {
        log.info("Updating profile item: {} for user: {}", itemId, userId);
        ProfileItem item = profileItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEMORY_ACCESS_DENIED);
        }
        item.setContent(content);
        item.setUpdatedAt(LocalDateTime.now());
        return profileItemRepository.save(item);
    }

    @Transactional
    public void deleteProfileItem(String userId, String itemId) {
        log.info("Deleting profile item: {} for user: {}", itemId, userId);
        ProfileItem item = profileItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEMORY_ACCESS_DENIED);
        }
        profileItemRepository.delete(item);
    }

    @Transactional
    public List<ProfileItem> replaceProfileItems(String userId, String profileText) {
        log.info("Replacing profile items for user: {}", userId);
        List<ProfileItem> existing = profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
        for (ProfileItem item : existing) {
            profileItemRepository.delete(item);
        }
        if (profileText != null && !profileText.isBlank()) {
            for (String line : profileText.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    ProfileItem item = ProfileItem.builder()
                            .id(UUID.randomUUID().toString())
                            .userId(userId)
                            .content(trimmed)
                            .createdAt(LocalDateTime.now())
                            .build();
                    profileItemRepository.save(item);
                }
            }
        }
        return profileItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    // ==================== Session Items ====================

    @Transactional(readOnly = true)
    public List<SessionItem> getSessionItems(String sessionId) {
        log.debug("Getting session items for session: {}", sessionId);
        return sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<SessionItem> getSessionItemsByUser(String userId) {
        log.debug("Getting all session items for user: {}", userId);
        return sessionItemRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional
    public SessionItem addSessionItem(String userId, String sessionId, String content) {
        log.info("Adding session item for user: {}, session: {}", userId, sessionId);
        SessionItem item = SessionItem.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(sessionId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        return sessionItemRepository.save(item);
    }

    @Transactional
    public void deleteSessionItem(String userId, String itemId) {
        log.info("Deleting session item: {} for user: {}", itemId, userId);
        SessionItem item = sessionItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMORY_NOT_FOUND));
        if (!item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.MEMORY_ACCESS_DENIED);
        }
        sessionItemRepository.delete(item);
    }

    @Transactional
    public void upsertSessionItem(String userId, String sessionId, String content) {
        List<SessionItem> existing = sessionItemRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (!existing.isEmpty()) {
            SessionItem item = existing.get(0);
            item.setContent(content);
            item.setUpdatedAt(LocalDateTime.now());
            sessionItemRepository.save(item);
            log.debug("Updated session item for session: {}", sessionId);
        } else {
            SessionItem item = SessionItem.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionId(sessionId)
                    .content(content)
                    .createdAt(LocalDateTime.now())
                    .build();
            sessionItemRepository.save(item);
            log.debug("Created session item for session: {}", sessionId);
        }
    }

    @Transactional
    public void deleteSessionItemsBySessionId(String sessionId) {
        log.info("Deleting session items for session: {}", sessionId);
        sessionItemRepository.deleteBySessionId(sessionId);
    }
}
