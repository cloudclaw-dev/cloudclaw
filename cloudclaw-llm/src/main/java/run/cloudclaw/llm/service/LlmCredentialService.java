package run.cloudclaw.llm.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.llm.encryption.AesEncryptionService;
import run.cloudclaw.llm.model.LlmCredential;
import run.cloudclaw.llm.repository.LlmCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCredentialService {

    private final LlmCredentialRepository credentialRepository;
    private final AesEncryptionService encryptionService;

    public List<LlmCredential> listByProvider(String providerId) {
        return credentialRepository.findByProviderId(providerId);
    }

    public List<LlmCredential> listAll() {
        return credentialRepository.findAll();
    }

    public LlmCredential getById(String id) {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Credential not found: " + id));
    }

    @Transactional
    public LlmCredential create(LlmCredential credential, String plainApiKey) {
        log.info("Creating credential '{}' for provider {}", credential.getName(), credential.getProviderId());
        credential.setApiKeyEncrypted(encryptionService.encrypt(plainApiKey));
        return credentialRepository.save(credential);
    }

    @Transactional
    public LlmCredential update(String id, String plainApiKey, Boolean enabled) {
        LlmCredential existing = getById(id);
        if (plainApiKey != null) {
            existing.setApiKeyEncrypted(encryptionService.encrypt(plainApiKey));
        }
        if (enabled != null) {
            existing.setEnabled(enabled);
        }
        return credentialRepository.save(existing);
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting credential: {}", id);
        credentialRepository.deleteById(id);
    }

    /**
     * Acquire an available credential for a provider, ordered by priority then weight.
     */
    public LlmCredential acquireCredential(String providerId) {
        List<LlmCredential> credentials = credentialRepository
                .findByProviderIdAndEnabledTrueOrderByPriorityAsc(providerId);

        LocalDateTime now = LocalDateTime.now();
        return credentials.stream()
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(now))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "No available credential for provider: " + providerId));
    }

    public String decryptKey(LlmCredential credential) {
        return encryptionService.decrypt(credential.getApiKeyEncrypted());
    }
}
