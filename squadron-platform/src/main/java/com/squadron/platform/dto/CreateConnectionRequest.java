package com.squadron.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConnectionRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotBlank(message = "Platform type is required")
    private String platformType;

    @NotBlank(message = "Base URL is required")
    private String baseUrl;

    @NotBlank(message = "Auth type is required")
    private String authType;

    private Map<String, String> credentials;

    private Map<String, Object> metadata;
}
