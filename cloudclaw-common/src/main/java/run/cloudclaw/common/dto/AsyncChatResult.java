package run.cloudclaw.common.dto;

import java.util.UUID;

/**
 * Result of an async chat message submission.
 */
public record AsyncChatResult(
    UUID userMessageId,
    UUID assistantMessageId,
    String status
) {}
