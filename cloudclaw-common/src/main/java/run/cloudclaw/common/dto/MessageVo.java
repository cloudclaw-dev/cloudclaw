package run.cloudclaw.common.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight message view for polling responses.
 */
public record MessageVo(
    UUID id,
    String role,
    String content,
    String status,
    LocalDateTime createdAt
) {}
