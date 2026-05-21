package run.cloudclaw.common.event;

/**
 * Published when a chat session is deleted.
 * Listeners can use this to clean up associated resources (e.g., sandbox sessions).
 */
public record SessionDeleteEvent(String sessionId) {
}
