package com.squadron.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for creating/updating a user's agent configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAgentConfigDto {

    private UUID id;

    private UUID tenantId;

    private UUID userId;

    @NotBlank(message = "Agent name is required")
    @Size(max = 100, message = "Agent name must not exceed 100 characters")
    private String agentName;

    @NotBlank(message = "Agent type is required")
    @Size(max = 50, message = "Agent type must not exceed 50 characters")
    private String agentType;

    private int displayOrder;

    private String provider;

    private String model;

    private Integer maxTokens;

    private Double temperature;

    private String systemPromptOverride;

    @Builder.Default
    private boolean enabled = true;

    private Instant createdAt;

    private Instant updatedAt;
}
