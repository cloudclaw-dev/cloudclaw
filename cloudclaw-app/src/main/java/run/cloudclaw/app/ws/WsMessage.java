package run.cloudclaw.app.ws;

import lombok.Data;

/**
 * Upstream WebSocket message DTO (client → server).
 *
 * <p>Supported actions:</p>
 * <ul>
 *   <li>{@code chat} — send a chat message (requires sessionId + message)</li>
 *   <li>{@code cancel} — cancel the active chat for a session</li>
 *   <li>{@code ping} — heartbeat probe</li>
 * </ul>
 */
@Data
public class WsMessage {

    private String action;
    private String sessionId;
    private String message;
    private String requestId;
}
