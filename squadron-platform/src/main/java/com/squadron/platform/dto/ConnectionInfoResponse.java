package com.squadron.platform.dto;

import com.squadron.platform.entity.PlatformConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe response DTO for PlatformConnection that omits sensitive credentials.
 * Lists available platform connections for linking without exposing secrets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionInfoResponse {

    private UUID id;
    private UUID tenantId;
    private String platformType;
    private String baseUrl;
    private String authType;
    private String status;
    private Instant createdAt;

    /**
     * Creates a safe response DTO from a PlatformConnection entity,
     * omitting sensitive credentials and metadata fields.
     */
    public static ConnectionInfoResponse fromEntity(PlatformConnection connection) {
        return ConnectionInfoResponse.builder()
                .id(connection.getId())
                .tenantId(connection.getTenantId())
                .platformType(connection.getPlatformType())
                .baseUrl(connection.getBaseUrl())
                .authType(connection.getAuthType())
                .status(connection.getStatus())
                .createdAt(connection.getCreatedAt())
                .build();
    }
}
