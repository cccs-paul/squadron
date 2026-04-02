package com.squadron.platform.dto;

import com.squadron.platform.entity.SshKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe response DTO for SSH keys that omits the private key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SshKeyResponse {

    private UUID id;
    private UUID tenantId;
    private UUID connectionId;
    private String name;
    private String publicKey;
    private String fingerprint;
    private String keyType;
    private Instant createdAt;
    private Instant updatedAt;

    public static SshKeyResponse fromEntity(SshKey entity) {
        return SshKeyResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .connectionId(entity.getConnectionId())
                .name(entity.getName())
                .publicKey(entity.getPublicKey())
                .fingerprint(entity.getFingerprint())
                .keyType(entity.getKeyType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
