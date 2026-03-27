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
public class ProjectDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private String name;
    private UUID connectionId;
    private String externalProjectId;
    private String repoUrl;
    private String defaultBranch;
    private String branchStrategy;
    private Map<String, Object> settings;
    private Instant createdAt;
    private Instant updatedAt;
}
