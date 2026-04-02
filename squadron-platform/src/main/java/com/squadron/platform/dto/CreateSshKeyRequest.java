package com.squadron.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSshKeyRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    @NotBlank(message = "Key name is required")
    private String name;

    @NotBlank(message = "Public key is required")
    private String publicKey;

    @NotBlank(message = "Private key is required")
    private String privateKey;

    /**
     * Key type: ED25519 or RSA. Defaults to ED25519 if not specified.
     */
    private String keyType;
}
