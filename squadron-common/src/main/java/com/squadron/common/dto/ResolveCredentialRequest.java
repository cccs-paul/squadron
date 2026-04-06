package com.squadron.common.dto;

import com.squadron.common.security.CredentialPurpose;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to resolve credentials for a user on a specific platform connection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCredentialRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    @NotNull(message = "Credential purpose is required")
    private CredentialPurpose purpose;
}
