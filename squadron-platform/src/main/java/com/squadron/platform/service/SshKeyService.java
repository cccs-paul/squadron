package com.squadron.platform.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.CreateSshKeyRequest;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.repository.SshKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SshKeyService {

    private final SshKeyRepository sshKeyRepository;
    private final TokenEncryptionService tokenEncryptionService;

    public SshKey createSshKey(CreateSshKeyRequest request) {
        String keyType = request.getKeyType() != null ? request.getKeyType().toUpperCase() : "ED25519";
        if (!keyType.equals("ED25519") && !keyType.equals("RSA")) {
            throw new IllegalArgumentException("Key type must be ED25519 or RSA");
        }

        String fingerprint = computeFingerprint(request.getPublicKey());

        if (sshKeyRepository.existsByTenantIdAndFingerprint(request.getTenantId(), fingerprint)) {
            throw new IllegalArgumentException("An SSH key with this fingerprint already exists for this tenant");
        }

        String encryptedPrivateKey = tokenEncryptionService.encrypt(request.getPrivateKey());

        SshKey sshKey = SshKey.builder()
                .tenantId(request.getTenantId())
                .connectionId(request.getConnectionId())
                .name(request.getName())
                .publicKey(request.getPublicKey())
                .privateKey(encryptedPrivateKey)
                .fingerprint(fingerprint)
                .keyType(keyType)
                .build();

        SshKey saved = sshKeyRepository.save(sshKey);
        log.info("Created SSH key '{}' (fingerprint: {}) for tenant {} and connection {}",
                saved.getName(), fingerprint, saved.getTenantId(), saved.getConnectionId());
        return saved;
    }

    @Transactional(readOnly = true)
    public SshKey getSshKey(UUID id) {
        return sshKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SSH key not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SshKey> listSshKeysByTenant(UUID tenantId) {
        return sshKeyRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<SshKey> listSshKeysByConnection(UUID connectionId) {
        return sshKeyRepository.findByConnectionId(connectionId);
    }

    @Transactional(readOnly = true)
    public List<SshKey> listSshKeysByTenantAndConnection(UUID tenantId, UUID connectionId) {
        return sshKeyRepository.findByTenantIdAndConnectionId(tenantId, connectionId);
    }

    public void deleteSshKey(UUID id) {
        SshKey sshKey = getSshKey(id);
        sshKeyRepository.delete(sshKey);
        log.info("Deleted SSH key '{}' (id: {})", sshKey.getName(), id);
    }

    /**
     * Returns the decrypted private key for use in Git operations.
     */
    @Transactional(readOnly = true)
    public String getDecryptedPrivateKey(UUID id) {
        SshKey sshKey = getSshKey(id);
        return tokenEncryptionService.decrypt(sshKey.getPrivateKey());
    }

    /**
     * Computes a SHA-256 fingerprint of the public key for deduplication.
     */
    String computeFingerprint(String publicKey) {
        try {
            // Extract the key data portion (handle "ssh-rsa AAAA..." or "ssh-ed25519 AAAA..." format)
            String keyData = publicKey.trim();
            String[] parts = keyData.split("\\s+");
            String base64Key = parts.length >= 2 ? parts[1] : parts[0];

            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyBytes);

            return "SHA256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (IllegalArgumentException e) {
            // If Base64 decoding fails, hash the raw key string
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(publicKey.getBytes());
                return "SHA256:" + HexFormat.of().formatHex(hash);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException("SHA-256 algorithm not available", ex);
            }
        }
    }
}
