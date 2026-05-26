package run.cloudclaw.auth.controller;

import run.cloudclaw.auth.service.AuthService;
import run.cloudclaw.common.dto.LoginRequest;
import run.cloudclaw.common.dto.LoginResponse;
import run.cloudclaw.common.dto.RefreshRequest;
import run.cloudclaw.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller providing login and token refresh endpoints.
 *
 * <p>Base path: {@code /api/v1/auth}</p>
 * <p>Both endpoints are publicly accessible (no authentication required).</p>
 * <p>Fix C3: Rate limiting is now enforced in AuthService.login rather than the controller.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${cloudclaw.auth.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticate a user and return JWT tokens.
     * Fix C3: Passes client IP to AuthService for per-IP rate limiting.
     *
     * @param request the login request with username and password
     * @return {@link Result} containing {@link LoginResponse} with access and refresh tokens
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("Login request received for username: {}", request.getUsername());
        LoginResponse response = authService.login(request, clientIp);
        return Result.ok(response);
    }

    /**
     * Refresh an access token using a valid refresh token.
     *
     * @param request the refresh request containing the refresh token
     * @return {@link Result} containing {@link LoginResponse} with a new access token
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Token refresh request received");
        LoginResponse response = authService.refreshToken(request);
        return Result.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty()) {
                return ip.split(",")[0].trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty()) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
