package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConnectionDto {

    private UUID id;
    private UUID tenantId;
    private String platformType;
    private String baseUrl;
    private String authType;
    private String status;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
