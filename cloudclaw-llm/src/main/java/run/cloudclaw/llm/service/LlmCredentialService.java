package run.cloudclaw.llm.service;

import run.cloudclaw.common.exception.BusinessException;
import run.cloudclaw.common.exception.ErrorCode;
import run.cloudclaw.common.util.CryptoService;
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
    // Fix H1: Use unified CryptoService instead of duplicate AesEncryptionService
    private final CryptoService cryptoService;

    public List<LlmCredential> listByProvider(String providerId) {
        return credentialRepository.findByProviderId(providerId);
    }

    public List<LlmCredential> listAll() {
        return credentialRepository.findAll();
    }

    public LlmCredential getById(String id) {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LLM_CREDENTIAL_NOT_FOUND, id));
    }

    @Transactional
    public LlmCredential create(LlmCredential credential, String plainApiKey) {
        log.info("Creating credential '{}' for provider {}", credential.getName(), credential.getProviderId());
        credential.setApiKeyEncrypted(cryptoService.encrypt(plainApiKey));
        return credentialRepository.save(credential);
    }

    @Transactional
    public LlmCredential update(String id, String plainApiKey, Boolean enabled) {
        LlmCredential existing = getById(id);
        if (plainApiKey != null) {
            existing.setApiKeyEncrypted(cryptoService.encrypt(plainApiKey));
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
                .orElseThrow(() -> new BusinessException(ErrorCode.LLM_NO_AVAILABLE_CREDENTIAL, providerId));
    }

    public String decryptKey(LlmCredential credential) {
        return cryptoService.decrypt(credential.getApiKeyEncrypted());
    }
}
