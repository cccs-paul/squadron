package com.squadron.platform.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.CreateSshKeyRequest;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.repository.SshKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SshKeyServiceTest {

    @Mock
    private SshKeyRepository sshKeyRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    private SshKeyService sshKeyService;

    private UUID tenantId;
    private UUID connectionId;

    @BeforeEach
    void setUp() {
        sshKeyService = new SshKeyService(sshKeyRepository, tokenEncryptionService);
        tenantId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
    }

    // --- createSshKey ---

    @Test
    void should_createSshKey_when_validRequest() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----")
                .keyType("ED25519")
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(false);
        when(tokenEncryptionService.encrypt(anyString())).thenReturn("encrypted-private-key");

        SshKey savedKey = SshKey.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey(request.getPublicKey())
                .privateKey("encrypted-private-key")
                .fingerprint("SHA256:abc123")
                .keyType("ED25519")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(sshKeyRepository.save(any(SshKey.class))).thenReturn(savedKey);

        SshKey result = sshKeyService.createSshKey(request);

        assertNotNull(result);
        assertEquals("Deploy Key", result.getName());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(connectionId, result.getConnectionId());
        verify(tokenEncryptionService).encrypt(request.getPrivateKey());
        verify(sshKeyRepository).save(any(SshKey.class));
    }

    @Test
    void should_createSshKey_when_keyTypeIsRSA() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("RSA Key")
                .publicKey("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC user@host")
                .privateKey("private-key-data")
                .keyType("rsa")
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(false);
        when(tokenEncryptionService.encrypt("private-key-data")).thenReturn("encrypted");
        when(sshKeyRepository.save(any(SshKey.class))).thenAnswer(inv -> inv.getArgument(0));

        SshKey result = sshKeyService.createSshKey(request);

        assertEquals("RSA", result.getKeyType());
    }

    @Test
    void should_createSshKey_when_keyTypeIsNull() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Default Key")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("private-key-data")
                .keyType(null)
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(false);
        when(tokenEncryptionService.encrypt("private-key-data")).thenReturn("encrypted");
        when(sshKeyRepository.save(any(SshKey.class))).thenAnswer(inv -> inv.getArgument(0));

        SshKey result = sshKeyService.createSshKey(request);

        assertEquals("ED25519", result.getKeyType());
    }

    @Test
    void should_throwIllegalArgument_when_invalidKeyType() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Bad Key")
                .publicKey("ssh-bad AAAA")
                .privateKey("private")
                .keyType("DSA")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> sshKeyService.createSshKey(request));
        verify(sshKeyRepository, never()).save(any());
    }

    @Test
    void should_throwIllegalArgument_when_duplicateFingerprint() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Duplicate Key")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("private-key-data")
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sshKeyService.createSshKey(request));
        assertTrue(ex.getMessage().contains("fingerprint already exists"));
        verify(sshKeyRepository, never()).save(any());
    }

    @Test
    void should_encryptPrivateKey_when_creating() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Encrypt Test")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("my-super-secret-key")
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(false);
        when(tokenEncryptionService.encrypt("my-super-secret-key")).thenReturn("encrypted-value");
        when(sshKeyRepository.save(any(SshKey.class))).thenAnswer(inv -> inv.getArgument(0));

        sshKeyService.createSshKey(request);

        ArgumentCaptor<SshKey> captor = ArgumentCaptor.forClass(SshKey.class);
        verify(sshKeyRepository).save(captor.capture());
        assertEquals("encrypted-value", captor.getValue().getPrivateKey());
    }

    @Test
    void should_computeFingerprint_when_creating() {
        CreateSshKeyRequest request = CreateSshKeyRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Fingerprint Test")
                .publicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host")
                .privateKey("private-key")
                .build();

        when(sshKeyRepository.existsByTenantIdAndFingerprint(eq(tenantId), anyString()))
                .thenReturn(false);
        when(tokenEncryptionService.encrypt(anyString())).thenReturn("encrypted");
        when(sshKeyRepository.save(any(SshKey.class))).thenAnswer(inv -> inv.getArgument(0));

        sshKeyService.createSshKey(request);

        ArgumentCaptor<SshKey> captor = ArgumentCaptor.forClass(SshKey.class);
        verify(sshKeyRepository).save(captor.capture());
        assertNotNull(captor.getValue().getFingerprint());
        assertTrue(captor.getValue().getFingerprint().startsWith("SHA256:"));
    }

    // --- getSshKey ---

    @Test
    void should_getSshKey_when_exists() {
        UUID keyId = UUID.randomUUID();
        SshKey key = SshKey.builder()
                .id(keyId)
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Test Key")
                .build();

        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        SshKey result = sshKeyService.getSshKey(keyId);

        assertEquals(keyId, result.getId());
        assertEquals("Test Key", result.getName());
    }

    @Test
    void should_throwNotFound_when_sshKeyMissing() {
        UUID keyId = UUID.randomUUID();
        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sshKeyService.getSshKey(keyId));
    }

    // --- listSshKeysByTenant ---

    @Test
    void should_listSshKeysByTenant_when_keysExist() {
        SshKey k1 = SshKey.builder().id(UUID.randomUUID()).tenantId(tenantId).name("K1").build();
        SshKey k2 = SshKey.builder().id(UUID.randomUUID()).tenantId(tenantId).name("K2").build();

        when(sshKeyRepository.findByTenantId(tenantId)).thenReturn(List.of(k1, k2));

        List<SshKey> result = sshKeyService.listSshKeysByTenant(tenantId);

        assertEquals(2, result.size());
    }

    @Test
    void should_returnEmptyList_when_noKeysForTenant() {
        when(sshKeyRepository.findByTenantId(tenantId)).thenReturn(List.of());

        List<SshKey> result = sshKeyService.listSshKeysByTenant(tenantId);

        assertTrue(result.isEmpty());
    }

    // --- listSshKeysByConnection ---

    @Test
    void should_listSshKeysByConnection_when_keysExist() {
        SshKey k1 = SshKey.builder().id(UUID.randomUUID()).connectionId(connectionId).name("K1").build();

        when(sshKeyRepository.findByConnectionId(connectionId)).thenReturn(List.of(k1));

        List<SshKey> result = sshKeyService.listSshKeysByConnection(connectionId);

        assertEquals(1, result.size());
    }

    // --- listSshKeysByTenantAndConnection ---

    @Test
    void should_listSshKeysByTenantAndConnection_when_keysExist() {
        SshKey k1 = SshKey.builder().id(UUID.randomUUID()).tenantId(tenantId).connectionId(connectionId).name("K1").build();

        when(sshKeyRepository.findByTenantIdAndConnectionId(tenantId, connectionId)).thenReturn(List.of(k1));

        List<SshKey> result = sshKeyService.listSshKeysByTenantAndConnection(tenantId, connectionId);

        assertEquals(1, result.size());
    }

    // --- deleteSshKey ---

    @Test
    void should_deleteSshKey_when_exists() {
        UUID keyId = UUID.randomUUID();
        SshKey key = SshKey.builder()
                .id(keyId)
                .tenantId(tenantId)
                .name("To Delete")
                .build();

        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        sshKeyService.deleteSshKey(keyId);

        verify(sshKeyRepository).delete(key);
    }

    @Test
    void should_throwNotFound_when_deletingMissingSshKey() {
        UUID keyId = UUID.randomUUID();
        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sshKeyService.deleteSshKey(keyId));
    }

    // --- getDecryptedPrivateKey ---

    @Test
    void should_getDecryptedPrivateKey_when_keyExists() {
        UUID keyId = UUID.randomUUID();
        SshKey key = SshKey.builder()
                .id(keyId)
                .tenantId(tenantId)
                .privateKey("encrypted-private-key")
                .build();

        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(tokenEncryptionService.decrypt("encrypted-private-key")).thenReturn("decrypted-private-key");

        String result = sshKeyService.getDecryptedPrivateKey(keyId);

        assertEquals("decrypted-private-key", result);
        verify(tokenEncryptionService).decrypt("encrypted-private-key");
    }

    @Test
    void should_throwNotFound_when_decryptingMissingSshKey() {
        UUID keyId = UUID.randomUUID();
        when(sshKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sshKeyService.getDecryptedPrivateKey(keyId));
    }

    // --- computeFingerprint ---

    @Test
    void should_computeFingerprint_when_validSshPublicKey() {
        String publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host";
        String fingerprint = sshKeyService.computeFingerprint(publicKey);

        assertNotNull(fingerprint);
        assertTrue(fingerprint.startsWith("SHA256:"));
        assertTrue(fingerprint.length() > 10);
    }

    @Test
    void should_computeFingerprint_when_invalidBase64FallsBackToRawHash() {
        String publicKey = "not-a-valid-ssh-key";
        String fingerprint = sshKeyService.computeFingerprint(publicKey);

        assertNotNull(fingerprint);
        assertTrue(fingerprint.startsWith("SHA256:"));
    }

    @Test
    void should_computeSameFingerprint_when_samePublicKey() {
        String publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKey user@host";

        String fp1 = sshKeyService.computeFingerprint(publicKey);
        String fp2 = sshKeyService.computeFingerprint(publicKey);

        assertEquals(fp1, fp2);
    }

    @Test
    void should_computeDifferentFingerprints_when_differentPublicKeys() {
        String key1 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKey1 user@host";
        String key2 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKey2 user@host";

        String fp1 = sshKeyService.computeFingerprint(key1);
        String fp2 = sshKeyService.computeFingerprint(key2);

        assertNotEquals(fp1, fp2);
    }
}
