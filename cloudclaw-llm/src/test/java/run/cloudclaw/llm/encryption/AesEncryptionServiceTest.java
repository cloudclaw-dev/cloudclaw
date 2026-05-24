package run.cloudclaw.llm.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AesEncryptionService")
class AesEncryptionServiceTest {

    private AesEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new AesEncryptionService("test-secret-key-at-least-32-characters!!");
    }

    @Test
    @DisplayName("加密后应能正确解密（round-trip）")
    void encryptDecrypt_roundTrip() {
        String original = "my-api-key-12345";
        String encrypted = service.encrypt(original);
        assertNotEquals(original, encrypted);
        assertEquals(original, service.decrypt(encrypted));
    }

    @Test
    @DisplayName("相同明文每次加密结果应不同（随机 IV）")
    void encrypt_differentEachTime() {
        String plaintext = "same-input";
        String encrypted1 = service.encrypt(plaintext);
        String encrypted2 = service.encrypt(plaintext);
        assertNotEquals(encrypted1, encrypted2); // 不同 IV 导致不同密文
    }

    @Test
    @DisplayName("不同密钥应无法解密")
    void decrypt_wrongKey_throws() {
        AesEncryptionService other = new AesEncryptionService("different-key-at-least-32-characters!!");
        String encrypted = service.encrypt("secret");
        assertThrows(RuntimeException.class, () -> other.decrypt(encrypted));
    }

    @Test
    @DisplayName("解密无效 Base64 应抛出异常")
    void decrypt_invalidBase64_throws() {
        assertThrows(RuntimeException.class, () -> service.decrypt("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("加密空字符串")
    void encryptDecrypt_emptyString() {
        String encrypted = service.encrypt("");
        assertEquals("", service.decrypt(encrypted));
    }

    @Test
    @DisplayName("加密中文字符")
    void encryptDecrypt_chinese() {
        String original = "智谱AI API密钥测试";
        String encrypted = service.encrypt(original);
        assertEquals(original, service.decrypt(encrypted));
    }

    @Test
    @DisplayName("加密长字符串")
    void encryptDecrypt_longString() {
        String original = "a".repeat(10000);
        String encrypted = service.encrypt(original);
        assertEquals(original, service.decrypt(encrypted));
    }

    @Test
    @DisplayName("SHA-256 派生密钥应始终生成有效 256-bit key")
    void shortKey_sha256Derivation() {
        AesEncryptionService shortKeyService = new AesEncryptionService("short");
        String original = "test-data";
        String encrypted = shortKeyService.encrypt(original);
        assertEquals(original, shortKeyService.decrypt(encrypted));
    }
}
