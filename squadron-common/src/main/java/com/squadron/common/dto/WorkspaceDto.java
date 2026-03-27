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
public class WorkspaceDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private UUID userId;
    private String providerType;
    private String containerId;
    private String status;
    private String repoUrl;
    private String branch;
    private String baseImage;
    private Map<String, Object> resourceLimits;
    private Instant createdAt;
    private Instant terminatedAt;
}
