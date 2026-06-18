package run.cloudclaw.llm.service;

import run.cloudclaw.common.config.ConfigChangeEvent;
import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.llm.model.LlmCredential;
import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.model.LlmProvider;
import run.cloudclaw.llm.repository.LlmModelRepository;
import run.cloudclaw.llm.repository.LlmProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import run.cloudclaw.llm.ThinkingDisabledConnector;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRouteService {

    private final LlmModelRepository modelRepository;
    private final LlmProviderRepository providerRepository;
    private final LlmCredentialService credentialService;
    private final ObjectMapper objectMapper;

    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, Object> chatModelCache = new ConcurrentHashMap<>();

    /**
     * Get a ChatClient by model ID, with caching.
     */
    public ChatClient getChatClient(String modelId) {
        return clientCache.computeIfAbsent(modelId, this::createChatClient);
    }

    /**
     * Invalidate cached client for a model.
     */
    public void invalidateCache(String modelId) {
        clientCache.remove(modelId);
        chatModelCache.remove(modelId);
    }

    /**
     * Clear all cached clients.
     */
    public void clearCache() {
        clientCache.clear();
        chatModelCache.clear();
    }

    /**
     * 监听配置变更事件，清除对应的 LLM 客户端缓存。
     * 当 llm model/provider/credential 变更时触发。
     */
    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        if ("llm".equals(event.entityType())) {
            String entityId = event.entityId();
            if (entityId == null || entityId.isBlank()) {
                clearCache();
                log.info("LLM 配置全量变更，已清除所有客户端缓存");
            } else {
                invalidateCache(entityId);
                log.info("LLM 配置变更，已清除 modelId={} 的客户端缓存", entityId);
            }
        }
    }

    /**
     * Get the ChatModel directly for tool-calling scenarios.
     */
    @SuppressWarnings("unchecked")
    public <T> T getChatModel(String modelId) {
        getChatClient(modelId);
        return (T) chatModelCache.get(modelId);
    }

    private ChatClient createChatClient(String modelId) {
        LlmModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LLM_MODEL_NOT_FOUND, modelId));

        LlmProvider provider = providerRepository.findById(model.getProviderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LLM_PROVIDER_NOT_FOUND, model.getProviderId()));

        LlmCredential credential = credentialService.acquireCredential(provider.getId());
        String apiKey = credentialService.decryptKey(credential);

        Map<String, Object> defaultParams = parseDefaultParams(model.getDefaultParams());

        // Use providerType to determine client creation strategy
        String providerType = provider.getProviderType();

        if ("deepseek".equals(providerType)) {
            return createDeepSeekClient(provider, apiKey, model, defaultParams);
        }

        return switch (providerType) {
            case "openai_compatible" -> createOpenAiCompatible(provider, apiKey, model, defaultParams);
            case "ollama" -> createOllamaCompatible(provider, model, defaultParams);
            default -> throw new BusinessException(ErrorCode.LLM_UNSUPPORTED_PROVIDER, provider.getProviderType());
        };
    }

    /**
     * Create ChatClient using Spring AI's dedicated DeepSeek support.
     * Handles DeepSeek-specific quirks (stream_options, reasoner models, etc.)
     */
    private ChatClient createDeepSeekClient(LlmProvider provider, String apiKey,
                                             LlmModel model, Map<String, Object> params) {
        String baseUrl = provider.getApiBase();
        // Remove trailing /v1 if present (DeepSeekApi handles path internally)
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }

        log.info("Creating DeepSeek client: baseUrl={}, model={}", baseUrl, model.getModelName());

        // Inject "thinking": {"type": "disabled"} via custom ClientHttpConnector
        // to avoid Spring AI 1.1.5's reasoning_content bug
        org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder =
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .clientConnector(new ThinkingDisabledConnector());

        org.springframework.http.client.SimpleClientHttpRequestFactory deepSeekRequestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        deepSeekRequestFactory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        deepSeekRequestFactory.setReadTimeout(java.time.Duration.ofSeconds(300));

        org.springframework.web.client.RestClient.Builder restClientBuilder =
                org.springframework.web.client.RestClient.builder()
                        .requestFactory(deepSeekRequestFactory);

        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        DeepSeekChatOptions.Builder optionsBuilder = DeepSeekChatOptions.builder()
                .model(model.getModelName());

        if (params.containsKey("temperature")) {
            optionsBuilder.temperature(((Number) params.get("temperature")).doubleValue());
        }
        if (model.getMaxOutput() != null) {
            optionsBuilder.maxTokens(model.getMaxOutput());
        }

        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        chatModelCache.put(model.getId(), chatModel);
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient createOpenAiCompatible(LlmProvider provider, String apiKey,
                                               LlmModel model, Map<String, Object> params) {
        String baseUrl = provider.getApiBase();
        String completionsPath = "/v1/chat/completions"; // default

        // Auto-detect version path from baseUrl for non-standard providers
        // e.g. https://open.bigmodel.cn/api/coding/paas/v4 → completionsPath = /v4/chat/completions
        java.util.regex.Matcher versionMatcher = java.util.regex.Pattern.compile("^(.+)/(v\\d+)$").matcher(baseUrl);
        if (versionMatcher.find()) {
            String version = versionMatcher.group(2);
            if (!"v1".equals(version)) {
                baseUrl = versionMatcher.group(1);
                completionsPath = "/" + version + "/chat/completions";
                log.info("Detected non-standard version path: {}, completionsPath={}", version, completionsPath);
            }
        }

        log.info("Creating OpenAI client: baseUrl={}, completionsPath={}, model={}", baseUrl, completionsPath, model.getModelName());

        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(300));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model.getModelName());

        if (params.containsKey("temperature")) {
            optionsBuilder.temperature(((Number) params.get("temperature")).doubleValue());
        }
        if (model.getMaxOutput() != null) {
            optionsBuilder.maxTokens(model.getMaxOutput());
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                .build();

        chatModelCache.put(model.getId(), chatModel);
        return ChatClient.builder(chatModel).build();
    }

    private ChatClient createOllamaCompatible(LlmProvider provider, LlmModel model,
                                               Map<String, Object> params) {
        org.springframework.http.client.SimpleClientHttpRequestFactory ollamaRequestFactory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        ollamaRequestFactory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        ollamaRequestFactory.setReadTimeout(java.time.Duration.ofSeconds(300));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(provider.getApiBase() + "/v1")
                .apiKey("ollama")
                .restClientBuilder(org.springframework.web.client.RestClient.builder()
                        .requestFactory(ollamaRequestFactory))
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model.getModelName());

        if (params.containsKey("temperature")) {
            optionsBuilder.temperature(((Number) params.get("temperature")).doubleValue());
        }
        if (model.getMaxOutput() != null) {
            optionsBuilder.maxTokens(model.getMaxOutput());
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                .build();

        chatModelCache.put(model.getId(), chatModel);
        return ChatClient.builder(chatModel).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDefaultParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse default params: {}", json, e);
            return Map.of();
        }
    }
}
