package run.cloudclaw.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        TokenProperties props = new TokenProperties();
        props.setSecret("test-secret-key-for-jwt-must-be-at-least-256-bits-long");
        props.setAccessTokenTtl(Duration.ofHours(2));
        props.setRefreshTokenTtl(Duration.ofDays(7));
        provider = new JwtTokenProvider(props);
        provider.init();
    }

    @Test
    @DisplayName("生成 access token 应能通过验证")
    void generateAndValidate() {
        String token = provider.generateAccessToken("user-1", "admin", "ADMIN");
        assertNotNull(token);
        assertTrue(provider.validateToken(token));
    }

    @Test
    @DisplayName("从 token 提取 userId")
    void extractUserId() {
        String token = provider.generateAccessToken("user-123", "testuser", "USER");
        assertEquals("user-123", provider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("从 token 提取 username")
    void extractUsername() {
        String token = provider.generateAccessToken("user-1", "admin", "ADMIN");
        assertEquals("admin", provider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("从 token 提取自定义 claim")
    void extractClaim() {
        String token = provider.generateAccessToken("user-1", "admin", "ADMIN");
        assertEquals("ADMIN", provider.getClaimFromToken(token, "role"));
    }

    @Test
    @DisplayName("不存在的 claim 应返回 null")
    void nonExistentClaim() {
        String token = provider.generateAccessToken("user-1", "admin", "ADMIN");
        assertNull(provider.getClaimFromToken(token, "nonexistent"));
    }

    @Test
    @DisplayName("无效 token 应验证失败")
    void invalidToken() {
        assertFalse(provider.validateToken("invalid.token.here"));
    }

    @Test
    @DisplayName("空 token 应验证失败")
    void emptyToken() {
        assertFalse(provider.validateToken(""));
    }

    @Test
    @DisplayName("null token 应验证失败")
    void nullToken() {
        assertFalse(provider.validateToken(null));
    }

    @Test
    @DisplayName("refresh token 应能通过验证")
    void refreshToken_valid() {
        String token = provider.generateRefreshToken("user-1");
        assertTrue(provider.validateToken(token));
        assertEquals("user-1", provider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("refresh token 的 type claim 应为 refresh")
    void refreshToken_type() {
        String token = provider.generateRefreshToken("user-1");
        assertEquals("refresh", provider.getClaimFromToken(token, "type"));
    }

    @Test
    @DisplayName("密钥太短应抛出 IllegalStateException")
    void shortSecret_throws() {
        TokenProperties props = new TokenProperties();
        props.setSecret("short"); // < 32 bytes
        JwtTokenProvider p = new JwtTokenProvider(props);
        assertThrows(IllegalStateException.class, p::init);
    }

    @Test
    @DisplayName("过期 token 应验证失败")
    void expiredToken() {
        TokenProperties props = new TokenProperties();
        props.setSecret("test-secret-key-for-jwt-must-be-at-least-256-bits-long");
        props.setAccessTokenTtl(Duration.ofMillis(-1)); // 已过期
        props.setRefreshTokenTtl(Duration.ofDays(7));
        JwtTokenProvider expiredProvider = new JwtTokenProvider(props);
        expiredProvider.init();

        String token = expiredProvider.generateAccessToken("user-1", "admin", "ADMIN");
        assertFalse(expiredProvider.validateToken(token));
    }
}
