package com.squadron.platform.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.CreateSshKeyRequest;
import com.squadron.platform.dto.GenerateDeployKeyRequest;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.repository.SshKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
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

    /**
     * Generates a new Ed25519 keypair server-side and stores it as a deploy key.
     */
    public SshKey generateDeployKey(GenerateDeployKeyRequest request) {
        KeyPair keyPair = generateEd25519KeyPair();

        String publicKeyStr = encodePublicKeyOpenSsh((EdECPublicKey) keyPair.getPublic(), request.getName());
        String privateKeyStr = encodePrivateKeyOpenSsh(keyPair);

        String fingerprint = computeFingerprint(publicKeyStr);

        if (sshKeyRepository.existsByTenantIdAndFingerprint(request.getTenantId(), fingerprint)) {
            throw new IllegalArgumentException("An SSH key with this fingerprint already exists for this tenant");
        }

        String encryptedPrivateKey = tokenEncryptionService.encrypt(privateKeyStr);

        String keyUsage = request.getKeyUsage() != null ? request.getKeyUsage() : "DEPLOY_KEY";

        SshKey sshKey = SshKey.builder()
                .tenantId(request.getTenantId())
                .connectionId(request.getConnectionId())
                .name(request.getName())
                .publicKey(publicKeyStr)
                .privateKey(encryptedPrivateKey)
                .fingerprint(fingerprint)
                .keyType("ED25519")
                .keyUsage(keyUsage)
                .build();

        SshKey saved = sshKeyRepository.save(sshKey);
        log.info("Generated deploy key '{}' (fingerprint: {}) for tenant {} and connection {}",
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

    // -----------------------------------------------------------------------
    // Ed25519 key generation and OpenSSH encoding helpers
    // -----------------------------------------------------------------------

    KeyPair generateEd25519KeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 algorithm not available in this JVM", e);
        }
    }

    /**
     * Encodes an Ed25519 public key in OpenSSH one-line format:
     * {@code ssh-ed25519 <base64> <comment>}
     */
    String encodePublicKeyOpenSsh(EdECPublicKey publicKey, String comment) {
        try {
            byte[] rawPoint = reverseBytes(publicKey.getPoint().getY().toByteArray());
            // Ed25519 public key is always 32 bytes
            byte[] keyData = new byte[32];
            System.arraycopy(rawPoint, 0, keyData, 0, Math.min(rawPoint.length, 32));
            // Set the high bit of the last byte if the x coordinate is odd
            if (publicKey.getPoint().isXOdd()) {
                keyData[31] |= (byte) 0x80;
            }

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            writeOpenSshString(out, "ssh-ed25519");
            writeOpenSshBytes(out, keyData);

            String base64 = Base64.getEncoder().encodeToString(buf.toByteArray());
            return "ssh-ed25519 " + base64 + " " + (comment != null ? comment : "squadron-deploy-key");
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode public key", e);
        }
    }

    /**
     * Encodes an Ed25519 keypair in OpenSSH PEM format (the format produced by
     * {@code ssh-keygen -t ed25519}).  This is a simplified, unencrypted encoding.
     */
    String encodePrivateKeyOpenSsh(KeyPair keyPair) {
        try {
            EdECPublicKey pub = (EdECPublicKey) keyPair.getPublic();
            EdECPrivateKey priv = (EdECPrivateKey) keyPair.getPrivate();

            byte[] pubKeyData = new byte[32];
            byte[] rawPoint = reverseBytes(pub.getPoint().getY().toByteArray());
            System.arraycopy(rawPoint, 0, pubKeyData, 0, Math.min(rawPoint.length, 32));
            if (pub.getPoint().isXOdd()) {
                pubKeyData[31] |= (byte) 0x80;
            }

            // The "seed" is the 32-byte private scalar; OpenSSH stores seed || public
            byte[] seed = priv.getBytes().orElseThrow(() -> new RuntimeException("Cannot extract Ed25519 private key bytes"));
            byte[] privKeyData = new byte[64];
            System.arraycopy(seed, 0, privKeyData, 0, 32);
            System.arraycopy(pubKeyData, 0, privKeyData, 32, 32);

            // Build the public key blob (same as the one-line format's base64 portion)
            ByteArrayOutputStream pubBlob = new ByteArrayOutputStream();
            DataOutputStream pubOut = new DataOutputStream(pubBlob);
            writeOpenSshString(pubOut, "ssh-ed25519");
            writeOpenSshBytes(pubOut, pubKeyData);

            // Build the private section (checkint, keytype, pubkey, privkey, comment, padding)
            int checkInt = (int) (Math.random() * Integer.MAX_VALUE);
            ByteArrayOutputStream privSection = new ByteArrayOutputStream();
            DataOutputStream privOut = new DataOutputStream(privSection);
            privOut.writeInt(checkInt);
            privOut.writeInt(checkInt);
            writeOpenSshString(privOut, "ssh-ed25519");
            writeOpenSshBytes(privOut, pubKeyData);
            writeOpenSshBytes(privOut, privKeyData);
            writeOpenSshString(privOut, "squadron-deploy-key"); // comment

            // Pad to block size (8 bytes for "none" cipher)
            int padLen = 8 - (privSection.size() % 8);
            if (padLen < 8) {
                for (int i = 1; i <= padLen; i++) {
                    privOut.writeByte(i);
                }
            }

            // Assemble the full key file
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            DataOutputStream fullOut = new DataOutputStream(full);
            full.write("openssh-key-v1\0".getBytes()); // AUTH_MAGIC
            writeOpenSshString(fullOut, "none");        // cipher
            writeOpenSshString(fullOut, "none");        // kdf
            writeOpenSshBytes(fullOut, new byte[0]);    // kdf options (empty)
            fullOut.writeInt(1);                         // number of keys
            writeOpenSshBytes(fullOut, pubBlob.toByteArray());  // public key
            writeOpenSshBytes(fullOut, privSection.toByteArray()); // private key section

            String base64 = Base64.getMimeEncoder(70, "\n".getBytes()).encodeToString(full.toByteArray());
            return "-----BEGIN OPENSSH PRIVATE KEY-----\n" + base64 + "\n-----END OPENSSH PRIVATE KEY-----\n";
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode private key", e);
        }
    }

    private static void writeOpenSshString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeOpenSshBytes(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    /**
     * Reverses a byte array in place (BigInteger.toByteArray() returns big-endian,
     * but Ed25519 uses little-endian encoding for the y-coordinate).
     */
    private static byte[] reverseBytes(byte[] input) {
        // Strip leading zero byte that BigInteger may add for sign
        int start = (input.length > 0 && input[0] == 0) ? 1 : 0;
        int len = input.length - start;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = input[input.length - 1 - i];
        }
        return result;
    }
}
