package run.cloudclaw.auth.service;

import run.cloudclaw.auth.repository.UserRepository;
import run.cloudclaw.auth.security.LoginRateLimiter;
import run.cloudclaw.auth.token.JwtTokenProvider;
import run.cloudclaw.auth.token.TokenProperties;
import run.cloudclaw.common.dto.LoginRequest;
import run.cloudclaw.common.dto.LoginResponse;
import run.cloudclaw.common.dto.RefreshRequest;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Authentication service handling login and token refresh operations.
 *
 * <p>Uses {@link JwtTokenProvider} for token generation and validation,
 * {@link UserRepository} for user lookup, and {@link PasswordEncoder}
 * for password verification.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final TokenProperties tokenProperties;

    // Fix C3: Per-IP rate limiter for login — max 5 requests per 60 seconds (1 minute)
    private static final LoginRateLimiter loginRateLimiter = new LoginRateLimiter(5, 60_000);

    static {
        // Periodically clean up expired rate limiter entries
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "login-rate-limiter-cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(loginRateLimiter::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Authenticate a user with username and password, returning JWT tokens.
     * Fix C3: Added clientIp parameter for per-IP rate limiting at the service layer.
     *
     * @param request  the login request containing username and password
     * @param clientIp the client IP address for rate limiting
     * @return {@link LoginResponse} with access and refresh tokens
     * @throws BusinessException if rate limited, user not found, account disabled, or password invalid
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        // Fix C3: Rate limit login attempts per client IP before any credential check
        if (!loginRateLimiter.tryAcquire(clientIp)) {
            log.warn("Login rate limit exceeded for IP: {}", clientIp);
            throw new BusinessException(ErrorCode.AUTH_RATE_LIMITED);
        }

        log.info("Login attempt for username: {}", request.getUsername());

        // Fix: 统一登录失败日志消息，不区分"用户不存在"和"密码错误"，防止用户名枚举攻击
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed: invalid credentials");
                    return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        if (!user.getEnabled()) {
            log.warn("Login failed: invalid credentials");
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: invalid credentials");
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String userId = user.getId().toString();
        String roleString = user.getRole().name().toLowerCase();
        String accessToken = jwtTokenProvider.generateAccessToken(userId, user.getUsername(), roleString);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        log.info("Login successful for username: {}, userId: {}", user.getUsername(), userId);

        return new LoginResponse(
                accessToken,
                refreshToken,
                tokenProperties.getAccessTokenTtl().getSeconds()
        );
    }

    /**
     * Refresh an access token using a valid refresh token.
     * Fix C4: Implements refresh token rotation — each use issues a new refresh token,
     * preventing token replay attacks.
     *
     * @param request the refresh request containing the refresh token
     * @return {@link LoginResponse} with a new access token and a new refresh token
     * @throws BusinessException if the refresh token is invalid or the user is not found
     */
    public LoginResponse refreshToken(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("Token refresh failed: invalid refresh token");
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Verify token type is "refresh"
        String tokenType = jwtTokenProvider.getClaimFromToken(refreshToken, "type");
        if (!"refresh".equals(tokenType)) {
            log.warn("Token refresh failed: token is not a refresh token for userId: {}", userId);
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> {
                    log.warn("Token refresh failed: user not found for userId: {}", userId);
                    return new BusinessException(ErrorCode.NOT_FOUND);
                });

        if (!user.getEnabled()) {
            log.warn("Token refresh failed: account disabled for userId: {}", userId);
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        String roleString = user.getRole().name().toLowerCase();
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                userId, user.getUsername(), roleString
        );

        // Fix C4: Issue a new refresh token on every use (rotation)
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        log.info("Token refresh successful for userId: {}", userId);

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                tokenProperties.getAccessTokenTtl().getSeconds()
        );
    }
}
