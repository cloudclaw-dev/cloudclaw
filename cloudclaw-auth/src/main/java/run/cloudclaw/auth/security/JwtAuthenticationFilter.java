package run.cloudclaw.auth.security;

import run.cloudclaw.auth.token.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that extracts and validates Bearer tokens from
 * the Authorization header on every request.
 *
 * <p>Stores the authentication in a request attribute so it can be restored
 * during ASYNC dispatches (when the Authorization header is no longer available).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_ATTR = "cloudclaw.jwt.auth";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // For ASYNC dispatches, restore auth from request attribute
        if (request.getDispatcherType() == DispatcherType.ASYNC
                || request.getDispatcherType() == DispatcherType.ERROR) {
            var saved = (UsernamePasswordAuthenticationToken) request.getAttribute(AUTH_ATTR);
            if (saved != null) {
                SecurityContextHolder.getContext().setAuthentication(saved);
                log.debug("Restored authentication from request attribute for {} dispatch", request.getDispatcherType());
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Normal request: extract JWT
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getClaimFromToken(token, "role");

            List<SimpleGrantedAuthority> authorities = List.of();
            if (StringUtils.hasText(role)) {
                authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            // Save to request attribute for async dispatch recovery
            request.setAttribute(AUTH_ATTR, authentication);

            log.debug("Authenticated user: userId={}, role={}", userId, role);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
