package com.squadron.orchestrator.dto;

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
public class CreateProjectRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Team ID is required")
    private UUID teamId;

    @NotBlank(message = "Project name is required")
    private String name;

    private String repoUrl;

    private String defaultBranch;

    private String branchStrategy;

    private UUID connectionId;

    private String externalProjectId;

    private String settings;
}
