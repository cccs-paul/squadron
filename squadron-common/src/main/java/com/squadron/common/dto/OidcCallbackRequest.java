package com.squadron.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcCallbackRequest {
    @NotBlank(message = "Authorization code is required")
    private String code;

    @NotBlank(message = "State parameter is required")
    private String state;

    private String redirectUri;
}
