package run.cloudclaw.auth.service;

import run.cloudclaw.auth.repository.UserRepository;
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

    /**
     * Authenticate a user with username and password, returning JWT tokens.
     *
     * @param request the login request containing username and password
     * @return {@link LoginResponse} with access and refresh tokens
     * @throws BusinessException if the user is not found, account is disabled, or password is invalid
     */
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found for username: {}", request.getUsername());
                    return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        if (!user.getEnabled()) {
            log.warn("Login failed: account disabled for username: {}", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: invalid password for username: {}", request.getUsername());
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
     *
     * @param request the refresh request containing the refresh token
     * @return {@link LoginResponse} with a new access token and the same refresh token
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

        log.info("Token refresh successful for userId: {}", userId);

        return new LoginResponse(
                newAccessToken,
                refreshToken,
                tokenProperties.getAccessTokenTtl().getSeconds()
        );
    }
}
