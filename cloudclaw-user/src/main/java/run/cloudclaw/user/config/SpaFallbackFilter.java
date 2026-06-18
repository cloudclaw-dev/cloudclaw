package run.cloudclaw.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SPA fallback: serves index.html for Vue Router paths.
 * All frontend routes (/, /memory, /dashboard, /agents, etc.) are handled by the single merged SPA.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/actuator") || path.startsWith("/ws/")) {
            return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // Static assets — let ResourceHandler deal with them
        if (isStaticAsset(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // All SPA paths forward to /chat/index.html
        request.getRequestDispatcher("/chat/index.html").forward(request, response);
    }

    private boolean isStaticAsset(String path) {
        return path.contains("/assets/") || path.endsWith(".js") || path.endsWith(".css")
                || path.endsWith(".png") || path.endsWith(".ico") || path.endsWith(".svg")
                || path.endsWith(".jpg") || path.endsWith(".woff2") || path.endsWith(".woff")
                || path.endsWith(".map") || path.endsWith(".html");
    }
}
