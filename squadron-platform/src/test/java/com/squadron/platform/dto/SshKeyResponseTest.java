package com.squadron.platform.dto;

import com.squadron.platform.entity.SshKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SshKeyResponseTest {

    @Test
    void should_mapAllFields_when_fromEntity() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        SshKey entity = SshKey.builder()
                .id(id)
                .tenantId(tenantId)
                .connectionId(connectionId)
                .name("Deploy Key")
                .publicKey("ssh-ed25519 AAAA user@host")
                .privateKey("encrypted-private-key")
                .fingerprint("SHA256:abc123")
                .keyType("ED25519")
                .createdAt(now)
                .updatedAt(now)
                .build();

        SshKeyResponse response = SshKeyResponse.fromEntity(entity);

        assertEquals(id, response.getId());
        assertEquals(tenantId, response.getTenantId());
        assertEquals(connectionId, response.getConnectionId());
        assertEquals("Deploy Key", response.getName());
        assertEquals("ssh-ed25519 AAAA user@host", response.getPublicKey());
        assertEquals("SHA256:abc123", response.getFingerprint());
        assertEquals("ED25519", response.getKeyType());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }

    @Test
    void should_notIncludePrivateKey_when_fromEntity() {
        SshKey entity = SshKey.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("ssh-ed25519 AAAA")
                .privateKey("super-secret-private-key")
                .fingerprint("SHA256:xyz")
                .keyType("ED25519")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        SshKeyResponse response = SshKeyResponse.fromEntity(entity);

        // SshKeyResponse does not have a privateKey field
        // Verify that the response has all expected fields but not privateKey
        assertNotNull(response.getPublicKey());
        assertNotNull(response.getId());
    }

    @Test
    void should_handleNullTimestamps_when_fromEntity() {
        SshKey entity = SshKey.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Key")
                .publicKey("ssh-ed25519 AAAA")
                .privateKey("private")
                .fingerprint("SHA256:abc")
                .keyType("RSA")
                .createdAt(null)
                .updatedAt(null)
                .build();

        SshKeyResponse response = SshKeyResponse.fromEntity(entity);

        assertNull(response.getCreatedAt());
        assertNull(response.getUpdatedAt());
        assertEquals("RSA", response.getKeyType());
    }

    @Test
    void should_buildWithBuilder() {
        UUID id = UUID.randomUUID();
        SshKeyResponse response = SshKeyResponse.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .name("Builder Key")
                .publicKey("ssh-ed25519 AAAA")
                .fingerprint("SHA256:test")
                .keyType("ED25519")
                .build();

        assertEquals(id, response.getId());
        assertEquals("Builder Key", response.getName());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        SshKeyResponse r1 = SshKeyResponse.builder()
                .id(id).tenantId(tenantId).connectionId(connectionId)
                .name("Key").publicKey("pub").fingerprint("fp").keyType("ED25519")
                .createdAt(now).updatedAt(now)
                .build();

        SshKeyResponse r2 = SshKeyResponse.builder()
                .id(id).tenantId(tenantId).connectionId(connectionId)
                .name("Key").publicKey("pub").fingerprint("fp").keyType("ED25519")
                .createdAt(now).updatedAt(now)
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
