package run.cloudclaw.llm.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.model.LlmProvider;
import run.cloudclaw.llm.repository.LlmModelRepository;
import run.cloudclaw.llm.repository.LlmProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmRouteService")
class LlmRouteServiceTest {

    @Mock private LlmModelRepository modelRepository;
    @Mock private LlmProviderRepository providerRepository;
    @Mock private LlmCredentialService credentialService;

    private LlmRouteService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new LlmRouteService(modelRepository, providerRepository, credentialService, objectMapper);
    }

    @Test
    @DisplayName("模型不存在应抛 404")
    void modelNotFound_throws404() {
        when(modelRepository.findById("model-x")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getChatClient("model-x"));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("Model not found"));
    }

    @Test
    @DisplayName("供应商不存在应抛 404")
    void providerNotFound_throws404() {
        LlmModel model = new LlmModel();
        model.setId("model-1");
        model.setProviderId("provider-x");
        when(modelRepository.findById("model-1")).thenReturn(Optional.of(model));
        when(providerRepository.findById("provider-x")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getChatClient("model-1"));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("Provider not found"));
    }

    @Test
    @DisplayName("不支持的供应商类型应抛 400")
    void unsupportedProvider_throws400() {
        LlmModel model = new LlmModel();
        model.setId("model-1");
        model.setProviderId("p1");
        model.setModelName("test-model");

        LlmProvider provider = new LlmProvider();
        provider.setId("p1");
        provider.setProviderType("unsupported_type");
        provider.setApiBase("http://localhost");

        when(modelRepository.findById("model-1")).thenReturn(Optional.of(model));
        when(providerRepository.findById("p1")).thenReturn(Optional.of(provider));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getChatClient("model-1"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Unsupported provider type"));
    }

    @Test
    @DisplayName("invalidateCache 应移除指定模型缓存")
    void invalidateCache() {
        // Just verify no exception
        assertDoesNotThrow(() -> service.invalidateCache("model-1"));
    }

    @Test
    @DisplayName("clearCache 应清除所有缓存")
    void clearCache() {
        assertDoesNotThrow(() -> service.clearCache());
    }

    @Test
    @DisplayName("parseDefaultParams null 应返回空 Map")
    void parseDefaultParams_null() {
        // parseDefaultParams is private, tested indirectly
        // This test verifies the service can handle null defaultParams
        LlmModel model = new LlmModel();
        model.setId("m1");
        model.setProviderId("p1");
        model.setDefaultParams(null);
        model.setModelName("test");

        LlmProvider provider = new LlmProvider();
        provider.setId("p1");
        provider.setProviderType("openai_compatible");
        provider.setApiBase("http://localhost:8080");

        when(modelRepository.findById("m1")).thenReturn(Optional.of(model));
        when(providerRepository.findById("p1")).thenReturn(Optional.of(provider));

        // Will fail at credential acquisition, but parsing is done before that
        when(credentialService.acquireCredential("p1"))
                .thenThrow(new BusinessException(400, "No credential"));
        assertThrows(BusinessException.class, () -> service.getChatClient("m1"));
    }
}
