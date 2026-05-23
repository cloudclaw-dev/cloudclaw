package run.cloudclaw.llm.service;

import run.cloudclaw.llm.model.LlmProvider;
import run.cloudclaw.llm.repository.LlmProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderRepository providerRepository;
    private final LlmRouteService llmRouteService;

    public List<LlmProvider> listAll() {
        return providerRepository.findAll();
    }

    public LlmProvider getById(String id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new run.cloudclaw.common.exception.BusinessException(404, "Provider not found: " + id));
    }

    @Transactional
    public LlmProvider create(LlmProvider provider) {
        log.info("Creating LLM provider: {}", provider.getName());
        return providerRepository.save(provider);
    }

    @Transactional
    public LlmProvider update(String id, LlmProvider updates) {
        LlmProvider existing = getById(id);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDisplayName() != null) existing.setDisplayName(updates.getDisplayName());
        if (updates.getApiBase() != null) existing.setApiBase(updates.getApiBase());
        if (updates.getProviderType() != null) existing.setProviderType(updates.getProviderType());
        if (updates.getEnabled() != null) existing.setEnabled(updates.getEnabled());
        LlmProvider saved = providerRepository.save(existing);
        llmRouteService.clearCache();
        return saved;
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting LLM provider: {}", id);
        providerRepository.deleteById(id);
        llmRouteService.clearCache();
    }

    @Transactional
    public void initPresets() {
        if (providerRepository.count() > 0) {
            return;
        }
        log.info("Initializing LLM provider presets");
        createPresetIfAbsent("openai", "OpenAI", "https://api.openai.com", "openai_compatible");
        createPresetIfAbsent("zhipu", "智谱AI", "https://open.bigmodel.cn/api/coding/paas/v4", "openai_compatible");
        createPresetIfAbsent("deepseek", "DeepSeek", "https://api.deepseek.com", "openai_compatible");
        createPresetIfAbsent("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode", "openai_compatible");
        createPresetIfAbsent("ollama", "Ollama", "http://localhost:11434", "ollama");
    }

    private void createPresetIfAbsent(String name, String displayName, String apiBase, String type) {
        if (providerRepository.findByName(name).isEmpty()) {
            LlmProvider p = new LlmProvider();
            p.setName(name);
            p.setDisplayName(displayName);
            p.setApiBase(apiBase);
            p.setProviderType(type);
            p.setEnabled(true);
            providerRepository.save(p);
        }
    }
}
