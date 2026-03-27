package com.squadron.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedConfigDto {

    private String configKey;
    private String resolvedValue;
    private String resolvedFrom;
    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
}
