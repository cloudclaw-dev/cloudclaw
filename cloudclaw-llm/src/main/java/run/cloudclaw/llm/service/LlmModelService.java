package run.cloudclaw.llm.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.llm.model.LlmModel;
import run.cloudclaw.llm.repository.LlmModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmModelService {

    private final LlmModelRepository modelRepository;

    public List<LlmModel> listAll() {
        return modelRepository.findAll();
    }

    public List<LlmModel> listByProvider(String providerId) {
        return modelRepository.findByProviderId(providerId);
    }

    public LlmModel getById(String id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Model not found: " + id));
    }

    @Transactional
    public LlmModel create(LlmModel model) {
        log.info("Creating LLM model: {} for provider {}", model.getModelName(), model.getProviderId());
        return modelRepository.save(model);
    }

    @Transactional
    public LlmModel update(String id, LlmModel updates) {
        LlmModel existing = getById(id);
        if (updates.getModelName() != null) existing.setModelName(updates.getModelName());
        if (updates.getDisplayName() != null) existing.setDisplayName(updates.getDisplayName());
        if (updates.getProviderId() != null) existing.setProviderId(updates.getProviderId());
        if (updates.getModelType() != null) existing.setModelType(updates.getModelType());
        if (updates.getContextWindow() != null) existing.setContextWindow(updates.getContextWindow());
        if (updates.getMaxOutput() != null) existing.setMaxOutput(updates.getMaxOutput());
        if (updates.getInputPrice() != null) existing.setInputPrice(updates.getInputPrice());
        if (updates.getOutputPrice() != null) existing.setOutputPrice(updates.getOutputPrice());
        if (updates.getCapabilities() != null) existing.setCapabilities(updates.getCapabilities());
        if (updates.getDefaultParams() != null) existing.setDefaultParams(updates.getDefaultParams());
        if (updates.getEnabled() != null) existing.setEnabled(updates.getEnabled());
        return modelRepository.save(existing);
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting LLM model: {}", id);
        modelRepository.deleteById(id);
    }
}
