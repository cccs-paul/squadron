package com.squadron.review.dto;

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
public class CreateReviewBotConfigRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    @NotBlank(message = "Bot username is required")
    private String botUsername;

    @NotBlank(message = "Bot access token is required")
    private String botAccessToken;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean autoAssign = true;
}
