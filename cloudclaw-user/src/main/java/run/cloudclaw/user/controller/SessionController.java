package run.cloudclaw.user.controller;

import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.PageResult;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.dto.SessionCreateRequest;
import run.cloudclaw.common.model.Session;
import run.cloudclaw.common.repository.SessionItemRepository;
import run.cloudclaw.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for session management.
 *
 * <p>Provides endpoints for creating, listing, retrieving, and deleting
 * chat sessions. All operations are scoped to the authenticated user
 * to enforce data isolation.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@CrossOrigin
public class SessionController {

    private final SessionService sessionService;
    private final SessionItemRepository sessionItemRepository;

    /**
     * Create a new chat session.
     */
    @PostMapping
    public Result<Session> createSession(@AuthUser String userId,
                                         @Valid @RequestBody SessionCreateRequest request) {
        log.info("User [{}] creating session with agentId={}", userId, request.getAgentId());
        Session session = sessionService.createSession(userId,
                request.getAgentId(), request.getTitle());
        return Result.ok(session);
    }

    /**
     * List sessions for the authenticated user with pagination.
     */
    @GetMapping
    public Result<PageResult<Session>> listSessions(@AuthUser String userId,
                                                     @RequestParam(required = false) String agentId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        log.debug("User [{}] listing sessions, agentId={}, page={}, size={}", userId, agentId, page, size);
        PageResult<Session> result = sessionService.listSessions(userId, agentId, page, size);
        return Result.ok(result);
    }

    /**
     * Get detailed information about a specific session.
     */
    @GetMapping("/{id}")
    public Result<Session> getSession(@AuthUser String userId, @PathVariable String id) {
        log.debug("User [{}] getting session [{}]", userId, id);
        Session session = sessionService.getSession(userId, id);
        return Result.ok(session);
    }

    /**
     * Delete a session and all its associated messages.
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteSession(@AuthUser String userId, @PathVariable String id) {
        log.info("User [{}] deleting session [{}]", userId, id);
        // Delete session context items for this session
        sessionItemRepository.deleteBySessionId(id);
        log.debug("Deleted session context items for session {}", id);
        sessionService.deleteSession(userId, id);
        return Result.ok();
    }
}
