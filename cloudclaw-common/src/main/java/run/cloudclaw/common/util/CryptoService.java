package run.cloudclaw.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring-managed encryption component.
 * Uses cloudclaw.crypto.secret from application config.
 */
@Component
public class CryptoService {

    private final AesCryptoUtil cryptoUtil;

    public CryptoService(@Value("${cloudclaw.crypto.secret:cloudclaw-default-crypto-secret-key-32b}") String secret) {
        this.cryptoUtil = new AesCryptoUtil(secret);
    }

    public String encrypt(String plaintext) {
        return cryptoUtil.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return cryptoUtil.decrypt(ciphertext);
    }
}
