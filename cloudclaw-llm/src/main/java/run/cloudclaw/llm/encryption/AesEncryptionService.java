package run.cloudclaw.llm.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import run.cloudclaw.common.util.AesCryptoUtil;

/**
 * Fix H1: Facade that delegates to AesCryptoUtil to eliminate duplicate AES encryption logic.
 * Preserved as a Spring component so existing callers in cloudclaw-llm don't need to change.
 */
@Slf4j
@Component
public class AesEncryptionService {

    private final AesCryptoUtil cryptoUtil;

    public AesEncryptionService(@Value("${cloudclaw.crypto.secret:}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "Encryption key must be configured via 'cloudclaw.crypto.secret'. " +
                "No default is provided for security reasons.");
        }
        this.cryptoUtil = new AesCryptoUtil(key);
    }

    public String encrypt(String plaintext) {
        return cryptoUtil.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return cryptoUtil.decrypt(ciphertext);
    }
}
