package com.squadron.common.dto;

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
public class PlatformLinkRequest {
    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    private String authorizationCode;  // For OAuth2 flow
    private String redirectUri;         // For OAuth2 flow
    private String accessToken;         // For PAT flow
    private String tokenType;           // "oauth2" or "pat"
}
