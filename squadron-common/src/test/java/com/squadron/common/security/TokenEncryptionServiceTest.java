package com.squadron.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        encryptionService = new TokenEncryptionService(key);
    }

    @Test
    void should_encryptAndDecrypt_when_validPlaintext() {
        String plaintext = "my-secret-token-12345";

        String encrypted = encryptionService.encrypt(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void should_returnNull_when_encryptingNull() {
        assertNull(encryptionService.encrypt(null));
    }

    @Test
    void should_returnEmpty_when_encryptingEmpty() {
        assertEquals("", encryptionService.encrypt(""));
    }

    @Test
    void should_returnNull_when_decryptingNull() {
        assertNull(encryptionService.decrypt(null));
    }

    @Test
    void should_returnEmpty_when_decryptingEmpty() {
        assertEquals("", encryptionService.decrypt(""));
    }

    @Test
    void should_produceDifferentCiphertexts_when_sameInputEncryptedTwice() {
        String plaintext = "same-input";

        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        // Due to random IV, same plaintext should produce different ciphertexts
        assertNotEquals(encrypted1, encrypted2);

        // Both should decrypt to the same plaintext
        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    void should_throwSecurityException_when_decryptingWithWrongKey() {
        String plaintext = "sensitive-data";
        String encrypted = encryptionService.encrypt(plaintext);

        // Create a new service with a different key
        byte[] otherKeyBytes = new byte[32];
        new SecureRandom().nextBytes(otherKeyBytes);
        SecretKey otherKey = new SecretKeySpec(otherKeyBytes, "AES");
        TokenEncryptionService otherService = new TokenEncryptionService(otherKey);

        assertThrows(SecurityException.class, () -> otherService.decrypt(encrypted));
    }

    @Test
    void should_handleLongStrings_when_encryptingAndDecrypting() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("long-token-data-");
        }
        String longPlaintext = sb.toString();

        String encrypted = encryptionService.encrypt(longPlaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(longPlaintext, decrypted);
    }

    @Test
    void should_handleUnicodeStrings_when_encryptingAndDecrypting() {
        String unicode = "Hello \u00e9\u00e8\u00ea \u4e16\u754c \ud83d\ude00";

        String encrypted = encryptionService.encrypt(unicode);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(unicode, decrypted);
    }

    @Test
    void should_returnTrue_when_isEncryptedCalledOnEncryptedData() {
        String encrypted = encryptionService.encrypt("test-data");
        assertTrue(encryptionService.isEncrypted(encrypted));
    }

    @Test
    void should_returnFalse_when_isEncryptedCalledOnNull() {
        assertFalse(encryptionService.isEncrypted(null));
    }

    @Test
    void should_returnFalse_when_isEncryptedCalledOnEmpty() {
        assertFalse(encryptionService.isEncrypted(""));
    }

    @Test
    void should_returnFalse_when_isEncryptedCalledOnNonBase64() {
        assertFalse(encryptionService.isEncrypted("not-base64-!!!"));
    }

    @Test
    void should_constructWithBase64Key_when_validKeyProvided() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        String base64Key = java.util.Base64.getEncoder().encodeToString(keyBytes);

        TokenEncryptionService service = new TokenEncryptionService(base64Key);
        String encrypted = service.encrypt("test");
        String decrypted = service.decrypt(encrypted);
        assertEquals("test", decrypted);
    }

    @Test
    void should_constructWithRandomKey_when_nullKeyProvided() {
        TokenEncryptionService service = new TokenEncryptionService((String) null);
        String encrypted = service.encrypt("test");
        String decrypted = service.decrypt(encrypted);
        assertEquals("test", decrypted);
    }
}
