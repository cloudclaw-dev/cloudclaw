package run.cloudclaw.app.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import run.cloudclaw.auth.token.JwtTokenProvider;

import java.util.Map;

/**
 * Handshake interceptor that validates the JWT token passed as a query parameter.
 *
 * <p>The client connects with: {@code ws://host/ws/chat?token=<JWT>}</p>
 *
 * <p>If the token is valid, userId and username are stored in the session attributes.
 * If invalid, the handshake is rejected with HTTP 401.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket handshake rejected: invalid or missing token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        String username = jwtTokenProvider.getUsernameFromToken(token);
        attributes.put("userId", userId);
        attributes.put("username", username);
        attributes.put("lastPing", System.currentTimeMillis());

        log.info("WebSocket handshake accepted for user {} ({})", username, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String query = servletRequest.getServletRequest().getQueryString();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if ("token".equals(kv[0]) && kv.length == 2) {
                        return kv[1];
                    }
                }
            }
        }
        // Fallback: check URI query
        String uriQuery = request.getURI().getQuery();
        if (uriQuery != null) {
            for (String param : uriQuery.split("&")) {
                String[] kv = param.split("=", 2);
                if ("token".equals(kv[0]) && kv.length == 2) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
