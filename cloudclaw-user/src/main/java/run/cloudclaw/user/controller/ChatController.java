package run.cloudclaw.user.controller;

import run.cloudclaw.agent.engine.ChatEngine;
import run.cloudclaw.auth.security.AuthUser;
import run.cloudclaw.common.dto.AsyncChatRequest;
import run.cloudclaw.common.dto.AsyncChatResult;
import run.cloudclaw.common.dto.ChatChunk;
import run.cloudclaw.common.dto.ChatRequest;
import run.cloudclaw.common.dto.PageResult;
import run.cloudclaw.common.dto.PollResult;
import run.cloudclaw.common.dto.Result;
import run.cloudclaw.common.model.Message;
import run.cloudclaw.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST controller for chat operations.
 *
 * <p>Provides endpoints for sending messages (with SSE streaming responses)
 * and retrieving message history for a session. All operations enforce
 * user data isolation by verifying session ownership.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatEngine chatEngine;
    private final SessionService sessionService;

    /**
     * Send a message in a session and receive a streaming response via Server-Sent Events.
     *
     * <p>The response is streamed as SSE events containing {@link ChatChunk} objects.
     * The stream ends with a {@code done=true} chunk to signal completion.</p>
     *
     * @param id      the session ID
     * @param userId  the authenticated user ID, injected from JWT
     * @param request the chat request containing the user message
     * @return a Flux of SSE events with chat chunks
     */
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@PathVariable String id,
                                                  @AuthUser String userId,
                                                  @Valid @RequestBody ChatRequest request) {
        log.info("User [{}] sending message to session [{}]", userId, id);

        // Verify session ownership before processing
        try {
            verifySessionOwnership(id, userId);
        } catch (Exception e) {
            // Return error as SSE event instead of throwing (which conflicts with text/event-stream content type)
            log.error("Session ownership check failed for session [{}]: {}", id, e.getMessage());
            ChatChunk errorChunk = ChatChunk.builder()
                    .content("Error: " + e.getMessage())
                    .toolCall(false)
                    .done(true)
                    .build();
            return Flux.just(ServerSentEvent.<ChatChunk>builder().data(errorChunk).build());
        }

        return chatEngine.chat(userId, id, request.getMessage())
                .map(chunk -> ServerSentEvent.<ChatChunk>builder().data(chunk).build())
                .doOnError(e -> log.error("Error during chat streaming for session [{}]: {}", id, e.getMessage(), e))
                .doOnComplete(() -> log.debug("Chat streaming completed for session [{}]", id))
                // Fix M5: On error, return a proper SSE error event instead of null/empty
                .onErrorResume(e -> {
                    ChatChunk errorChunk = ChatChunk.builder()
                            .content("")
                            .type("error")
                            .errorCode(500)
                            .done(true)
                            .build();
                    return Flux.just(ServerSentEvent.<ChatChunk>builder().data(errorChunk).build());
                });
    }

    /**
     * Async send: submit a message and return immediately.
     * LLM processes in background. Results retrieved via polling.
     */
    @PostMapping("/{id}/send")
    public Result<AsyncChatResult> send(@PathVariable String id,
                                        @AuthUser String userId,
                                        @Valid @RequestBody AsyncChatRequest request) {
        log.info("User [{}] async send to session [{}]", userId, id);
        verifySessionOwnership(id, userId);
        return Result.ok(chatEngine.chatAsync(userId, id, request.getMessage(), request.getRequestId()));
    }

    /**
     * Poll for new messages after a given message ID.
     */
    @GetMapping("/{id}/messages/poll")
    public Result<PollResult> pollMessages(@PathVariable String id,
                                           @AuthUser String userId,
                                           @RequestParam(required = false) String after) {
        verifySessionOwnership(id, userId);
        return Result.ok(sessionService.pollMessages(id, after));
    }

    /**
     * Get the message history for a session with pagination.
     *
     * <p>Messages are returned in chronological order. Verifies session
     * ownership before returning data.</p>
     *
     * @param id     the session ID
     * @param userId the authenticated user ID, injected from JWT
     * @param page   page number (1-based), defaults to 1
     * @param size   page size, defaults to 50
     * @return paginated list of messages in the session
     */
    @GetMapping("/{id}/messages")
    public Result<PageResult<Message>> getMessages(@PathVariable String id,
                                                    @AuthUser String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        // Fix M7: Cap page size at 100
        size = Math.min(size, 100);
        // Fix: 分页参数加上限 Math.min(size, 100)，防止一次查询过多数据
        size = Math.min(size, 100);
        log.debug("User [{}] getting messages for session [{}], page={}, size={}", userId, id, page, size);

        // Verify session ownership before returning messages
        verifySessionOwnership(id, userId);

        PageResult<Message> result = sessionService.getMessages(id, page, size);
        return Result.ok(result);
    }

    /**
     * Verify that the given session belongs to the authenticated user.
     *
     * @param sessionId the session ID to check
     * @param userId    the authenticated user ID
     * @throws BusinessException if the session does not belong to the user
     */
    private void verifySessionOwnership(String sessionId, String userId) {
        // SessionService.getSession already verifies ownership (userId vs session's userId)
        sessionService.getSession(userId, sessionId);
    }
}
