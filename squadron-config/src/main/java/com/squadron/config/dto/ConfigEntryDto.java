package com.squadron.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigEntryDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private String configKey;
    private String configValue;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
}
