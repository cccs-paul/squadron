package com.squadron.platform.dto;

import com.squadron.platform.entity.UserPlatformToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe response DTO for UserPlatformToken that omits sensitive token values.
 * Never exposes raw or encrypted accessToken/refreshToken in API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformTokenResponse {

    private UUID id;
    private UUID userId;
    private UUID connectionId;
    private String tokenType;
    private String scopes;
    private Instant expiresAt;
    private boolean hasRefreshToken;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates a safe response DTO from a UserPlatformToken entity,
     * omitting sensitive accessToken and refreshToken fields.
     */
    public static UserPlatformTokenResponse fromEntity(UserPlatformToken token) {
        return UserPlatformTokenResponse.builder()
                .id(token.getId())
                .userId(token.getUserId())
                .connectionId(token.getConnectionId())
                .tokenType(token.getTokenType())
                .scopes(token.getScopes())
                .expiresAt(token.getExpiresAt())
                .hasRefreshToken(token.getRefreshToken() != null)
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt())
                .build();
    }
}
