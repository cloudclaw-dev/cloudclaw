package run.cloudclaw.auth.controller;

import run.cloudclaw.auth.repository.ChannelBindingRepository;
import run.cloudclaw.auth.repository.UserRepository;
import run.cloudclaw.auth.service.ChannelConfigService;
import run.cloudclaw.auth.token.JwtTokenProvider;
import run.cloudclaw.common.dto.LoginResponse;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.model.ChannelBinding;
import run.cloudclaw.common.model.ChannelConfig;
import run.cloudclaw.common.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Feishu OAuth login controller.
 *
 * <p>Handles the OAuth 2.0 authorization code flow for Feishu login:</p>
 * <ol>
 *   <li>GET /feishu/authorize — redirect to Feishu authorization page</li>
 *   <li>GET /feishu/callback — handle callback, create/bind user, issue JWT</li>
 *   <li>GET /feishu/bind — initiate binding for an already-logged-in user</li>
 *   <li>GET /feishu/bind/callback — handle binding callback</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/feishu")
@RequiredArgsConstructor
public class FeishuAuthController {

    private final ChannelConfigService channelConfigService;
    private final ChannelBindingRepository channelBindingRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${cloudclaw.frontend-url:${cloudclaw.auth.feishu.frontend-url:http://localhost:8080}}")
    private String frontendUrl;

    private static final String FEISHU_AUTH_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";
    private static final String FEISHU_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";
    private static final String FEISHU_USER_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    private static final String CHANNEL_TYPE = "feishu";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Step 1: Redirect user to Feishu authorization page.
     */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        ChannelConfig config = channelConfigService.findLoginConfig(CHANNEL_TYPE);
        if (config == null) {
            throw new BusinessException(ErrorCode.CHANNEL_DISABLED, "No Feishu login channel configured (need purpose=login or both)");
        }

        String state = UUID.randomUUID().toString();
        String redirectUri = config.getRedirectUri();
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "/api/v1/auth/feishu/callback";
        }
        // Feishu requires full URL for redirect_uri
        if (redirectUri.startsWith("/")) {
            redirectUri = frontendUrl + redirectUri;
        }

        String url = FEISHU_AUTH_URL
                + "?app_id=" + config.getAppId()
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;

        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    /**
     * Step 2: Handle Feishu OAuth callback.
     * Exchange code for token, get user info, create/find CloudClaw user, issue JWT.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        log.info("Feishu OAuth callback received, code={}", code.substring(0, Math.min(8, code.length())));

        try {
            FeishuUserInfo feishuUser = getFeishuUserInfo(code);
            LoginResponse loginResponse = handleFeishuLogin(feishuUser);

            // Redirect to frontend with tokens
            String redirectUrl = frontendUrl + "/login"
                    + "?access_token=" + loginResponse.getAccessToken()
                    + "&refresh_token=" + loginResponse.getRefreshToken()
                    + "&expires_in=" + loginResponse.getExpiresIn();

            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        } catch (BusinessException e) {
            log.error("Feishu OAuth failed: {}", e.getMessage());
            String redirectUrl = frontendUrl + "/login?error=feishu_oauth_failed&message=" +
                    URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        } catch (Exception e) {
            log.error("Feishu OAuth error", e);
            String redirectUrl = frontendUrl + "/login?error=feishu_oauth_failed";
            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        }
    }

    /**
     * Step 3: Initiate Feishu binding for an already-logged-in user.
     * The JWT token is passed as query parameter to identify the user.
     */
    @GetMapping("/bind")
    public ResponseEntity<Void> bind(@RequestParam String token) {
        // Validate JWT to get userId
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        String userId = jwtTokenProvider.getUserIdFromToken(token);

        ChannelConfig config = channelConfigService.findLoginConfig(CHANNEL_TYPE);
        if (config == null) {
            throw new BusinessException(ErrorCode.CHANNEL_DISABLED, "No Feishu login channel configured");
        }

        // Build auth URL with token as state
        String baseRedirectUri = config.getRedirectUri();
        String bindCallbackPath = "/api/v1/auth/feishu/bind/callback";
        String redirectUri;
        if (baseRedirectUri != null && !baseRedirectUri.isBlank()) {
            // Derive bind callback from the base domain
            if (baseRedirectUri.startsWith("http")) {
                String origin = baseRedirectUri.replaceFirst("(https?://[^/]+).*", "$1");
                redirectUri = origin + bindCallbackPath;
            } else {
                redirectUri = frontendUrl + bindCallbackPath;
            }
        } else {
            redirectUri = frontendUrl + bindCallbackPath;
        }
        String url = FEISHU_AUTH_URL
                + "?app_id=" + config.getAppId()
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + token;

        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    /**
     * Step 4: Handle Feishu binding callback.
     */
    @GetMapping("/bind/callback")
    public ResponseEntity<Void> bindCallback(@RequestParam String code, @RequestParam String state) {
        // state contains the JWT token
        if (!jwtTokenProvider.validateToken(state)) {
            String redirectUrl = frontendUrl + "/profile?error=invalid_token";
            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        }

        String userId = jwtTokenProvider.getUserIdFromToken(state);

        try {
            FeishuUserInfo feishuUser = getFeishuUserInfo(code);
            bindFeishuUser(userId, feishuUser);

            String redirectUrl = frontendUrl + "/profile?bound=feishu";
            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        } catch (Exception e) {
            log.error("Feishu binding failed", e);
            String redirectUrl = frontendUrl + "/profile?error=bind_failed&message=" +
                    URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(302).location(URI.create(redirectUrl)).build();
        }
    }

    // ===== Internal methods =====

    private LoginResponse handleFeishuLogin(FeishuUserInfo feishuUser) {
        // Check if already bound
        ChannelBinding binding = channelBindingRepository
                .findByChannelTypeAndChannelUserId(CHANNEL_TYPE, feishuUser.openId)
                .orElse(null);

        User user;
        if (binding != null) {
            // Existing binding — find user
            user = userRepository.findById(UUID.fromString(binding.getUserId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Bound user not found"));
            log.info("Feishu user {} logged in as CloudClaw user {}", feishuUser.openId, user.getUsername());
        } else {
            // Auto-register
            user = autoRegisterFeishuUser(feishuUser);
            log.info("Feishu user {} auto-registered as CloudClaw user {}", feishuUser.openId, user.getUsername());
        }

        // Issue JWT
        String tokenUserId = user.getId().toString();
        String roleString = user.getRole().name().toLowerCase();
        String accessToken = jwtTokenProvider.generateAccessToken(tokenUserId, user.getUsername(), roleString);
        String refreshToken = jwtTokenProvider.generateRefreshToken(tokenUserId);

        return new LoginResponse(accessToken, refreshToken, 7200L);
    }

    private User autoRegisterFeishuUser(FeishuUserInfo feishuUser) {
        // Create user with feishu info
        String username = "feishu_" + feishuUser.openId.substring(0, Math.min(8, feishuUser.openId.length()));

        // Ensure username uniqueness
        int suffix = 1;
        String baseUsername = username;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + "_" + suffix++;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // random password
        user.setEmail(username + "@feishu.local");
        user.setDisplayName(feishuUser.name);
        user.setAvatarUrl(feishuUser.avatarUrl);
        user.setRole(User.UserRole.USER);
        user.setEnabled(true);
        user = userRepository.save(user);

        // Create binding
        ChannelBinding binding = new ChannelBinding();
        binding.setUserId(user.getId().toString());
        binding.setChannelType(CHANNEL_TYPE);
        binding.setChannelUserId(feishuUser.openId);
        try {
            binding.setChannelData(objectMapper.writeValueAsString(Map.of(
                    "name", feishuUser.name != null ? feishuUser.name : "",
                    "avatarUrl", feishuUser.avatarUrl != null ? feishuUser.avatarUrl : "",
                    "openId", feishuUser.openId
            )));
        } catch (Exception ignored) {}
        channelBindingRepository.save(binding);

        return user;
    }

    private void bindFeishuUser(String userId, FeishuUserInfo feishuUser) {
        // Check if this feishu account is already bound to another user
        channelBindingRepository.findByChannelTypeAndChannelUserId(CHANNEL_TYPE, feishuUser.openId)
                .ifPresent(existing -> {
                    if (!existing.getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.CHANNEL_ALREADY_BOUND,
                                "This Feishu account is already bound to another user");
                    }
                });

        // Check if this user already has a feishu binding
        channelBindingRepository.findByUserIdAndChannelType(userId, CHANNEL_TYPE)
                .ifPresent(existing -> {
                    // Update existing binding
                    existing.setChannelUserId(feishuUser.openId);
                    existing.setChannelData(toChannelData(feishuUser));
                    channelBindingRepository.save(existing);
                    return;
                });

        // Create new binding
        ChannelBinding binding = new ChannelBinding();
        binding.setUserId(userId);
        binding.setChannelType(CHANNEL_TYPE);
        binding.setChannelUserId(feishuUser.openId);
        binding.setChannelData(toChannelData(feishuUser));
        channelBindingRepository.save(binding);
    }

    private String toChannelData(FeishuUserInfo user) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "name", user.name != null ? user.name : "",
                    "avatarUrl", user.avatarUrl != null ? user.avatarUrl : "",
                    "openId", user.openId
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Exchange authorization code for user_access_token, then get user info.
     */
    private FeishuUserInfo getFeishuUserInfo(String code) {
        ChannelConfig config = channelConfigService.findLoginConfig(CHANNEL_TYPE);
        if (config == null) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_CONFIGURED, "No Feishu login channel configured");
        }
        String appSecret = channelConfigService.getDecryptedSecret(config);
        String appId = config.getAppId();

        // Step 1: Get app_access_token (for token exchange)
        String appAccessToken = getAppAccessToken(appId, appSecret);

        // Step 2: Exchange code for user_access_token
        String userAccessToken = exchangeCodeForToken(appAccessToken, code);

        // Step 3: Get user info
        return getUserInfo(userAccessToken);
    }

    private String getAppAccessToken(String appId, String appSecret) {
        try {
            String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (json.path("code").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED,
                        "Failed to get app_access_token: " + json.path("msg").asText());
            }
            return json.path("app_access_token").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED, "Feishu API error: " + e.getMessage());
        }
    }

    private String exchangeCodeForToken(String appAccessToken, String code) {
        try {
            String body = "{\"grant_type\":\"authorization_code\",\"code\":\"" + code + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_TOKEN_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + appAccessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (json.path("code").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED,
                        "Failed to exchange code: " + json.path("msg").asText());
            }
            return json.path("data").path("access_token").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED, "Feishu token exchange error: " + e.getMessage());
        }
    }

    private FeishuUserInfo getUserInfo(String userAccessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_USER_URL))
                    .header("Authorization", "Bearer " + userAccessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (json.path("code").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED,
                        "Failed to get user info: " + json.path("msg").asText());
            }

            JsonNode data = json.path("data");
            return new FeishuUserInfo(
                    data.path("open_id").asText(),
                    data.path("name").asText(""),
                    data.path("avatar_url").asText("")
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CHANNEL_OAUTH_FAILED, "Feishu user info error: " + e.getMessage());
        }
    }

    // Internal DTO
    private record FeishuUserInfo(String openId, String name, String avatarUrl) {}
}
