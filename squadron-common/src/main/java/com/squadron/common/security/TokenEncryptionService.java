package com.squadron.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

@Service
public class TokenEncryptionService {
    private static final Logger log = LoggerFactory.getLogger(TokenEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    // AES-GCM: 12-byte IV + at least 16-byte auth tag = 28 bytes minimum
    private static final int MIN_ENCRYPTED_BYTES = GCM_IV_LENGTH + 16;
    private static final String PBKDF2_SALT = "squadron-token-encryption";
    private static final int PBKDF2_ITERATIONS = 10000;

    private final SecretKey secretKey;

    @Autowired
    public TokenEncryptionService(@Value("${squadron.security.encryption-key:#{null}}") String encryptionKey) {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            byte[] keyBytes = deriveKeyBytes(encryptionKey);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            log.error("No encryption key configured (squadron.security.encryption-key). "
                    + "Using ephemeral random key — encrypted data will NOT survive restarts. "
                    + "This is NOT suitable for production!");
            byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    // Constructor for testing with a specific key
    public TokenEncryptionService(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Derive a 32-byte AES-256 key from the provided encryption key string.
     * If the string is valid Base64 and decodes to exactly 16, 24, or 32 bytes,
     * use it directly. Otherwise, use PBKDF2WithHmacSHA256 to derive a 256-bit key.
     */
    private static byte[] deriveKeyBytes(String encryptionKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptionKey);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Not valid Base64; fall through to PBKDF2 derivation
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(
                    encryptionKey.toCharArray(),
                    PBKDF2_SALT.getBytes(StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    256);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new SecurityException("Failed to derive encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt token", e);
        }
    }

    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            // Must have at least 12-byte IV + 16-byte GCM auth tag = 28 bytes
            return decoded.length >= MIN_ENCRYPTED_BYTES;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
