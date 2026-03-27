package com.squadron.workspace.dto;

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
public class WorkspaceSpec {

    private UUID tenantId;
    private UUID taskId;
    private UUID userId;
    private String repoUrl;
    private String branch;
    private String baseImage;
    private Map<String, Object> resourceLimits;
    private String providerType;
}
