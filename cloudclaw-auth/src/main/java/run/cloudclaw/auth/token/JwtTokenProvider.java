package run.cloudclaw.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token provider for generating, validating, and parsing JWT tokens.
 *
 * <p>Uses HMAC-SHA256 signing. Stores userId as the subject claim,
 * username and role as custom claims.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final TokenProperties tokenProperties;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        String secret = tokenProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret key must be configured via 'cloudclaw.jwt.secret' or 'JWT_SECRET' env variable. " +
                "No default is provided for security reasons.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret key must be at least 256 bits (32 bytes). "
                            + "Current length: " + keyBytes.length + " bytes. "
                            + "Please configure 'cloudclaw.jwt.secret' with a sufficiently long value."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT token provider initialized with key size: {} bytes", keyBytes.length);
    }

    /**
     * Generate an access token containing userId, username, and role claims.
     *
     * @param userId   the user's unique identifier (stored as subject)
     * @param username the user's username (stored as custom claim)
     * @param role     the user's role (stored as custom claim)
     * @return signed JWT access token string
     */
    public String generateAccessToken(String userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenProperties.getAccessTokenTtl().toMillis());

        String token = Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();

        log.debug("Generated access token for user: {} (userId: {})", username, userId);
        return token;
    }

    /**
     * Generate a refresh token containing only the userId.
     *
     * @param userId the user's unique identifier (stored as subject)
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenProperties.getRefreshTokenTtl().toMillis());

        String token = Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();

        log.debug("Generated refresh token for userId: {}", userId);
        return token;
    }

    /**
     * Validate a JWT token.
     *
     * @param token the JWT token string
     * @return {@code true} if the token is valid and not expired, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extract the userId (subject) from a JWT token.
     *
     * @param token the JWT token string
     * @return the userId stored as the token subject
     */
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extract the username from a JWT token's custom claims.
     *
     * @param token the JWT token string
     * @return the username claim value
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, "username");
    }

    /**
     * Extract a specific claim from a JWT token.
     *
     * @param token     the JWT token string
     * @param claimName the name of the claim to extract
     * @return the claim value as a String, or {@code null} if not present
     */
    public String getClaimFromToken(String token, String claimName) {
        Claims claims = getClaims(token);
        Object value = claims.get(claimName);
        return value != null ? value.toString() : null;
    }

    /**
     * Parse and return all claims from a JWT token.
     *
     * @param token the JWT token string
     * @return the parsed Claims
     * @throws JwtException if the token is invalid
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
