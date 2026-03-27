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
public class OAuth2LinkRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    @NotBlank(message = "Authorization code is required")
    private String authorizationCode;

    @NotBlank(message = "Redirect URI is required")
    private String redirectUri;
}
