package run.cloudclaw.auth.controller;

import run.cloudclaw.auth.service.AuthService;
import run.cloudclaw.common.dto.LoginRequest;
import run.cloudclaw.common.dto.LoginResponse;
import run.cloudclaw.common.dto.RefreshRequest;
import run.cloudclaw.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller providing login and token refresh endpoints.
 *
 * <p>Base path: {@code /api/v1/auth}</p>
 * <p>Both endpoints are publicly accessible (no authentication required).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticate a user and return JWT tokens.
     *
     * @param request the login request with username and password
     * @return {@link Result} containing {@link LoginResponse} with access and refresh tokens
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Login request received for username: {}", request.getUsername());
        LoginResponse response = authService.login(request);
        return Result.ok(response);
    }

    /**
     * Refresh an access token using a valid refresh token.
     *
     * @param request the refresh request containing the refresh token
     * @return {@link Result} containing {@link LoginResponse} with a new access token
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        log.info("Token refresh request received");
        LoginResponse response = authService.refreshToken(request);
        return Result.ok(response);
    }
}
