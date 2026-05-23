package run.cloudclaw.common.dto;

import java.util.List;

/**
 * Result of polling for new messages.
 */
public record PollResult(
    List<MessageVo> messages,
    boolean hasMore
) {}
