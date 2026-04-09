package com.squadron.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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

    private UUID tenantId;

    private UUID teamId;

    @NotBlank(message = "Project name is required")
    private String name;

    @JsonProperty("repositoryUrl")
    @JsonAlias("repoUrl")
    private String repoUrl;

    private String defaultBranch;

    private String branchStrategy;

    private String branchNamingTemplate;

    private UUID connectionId;

    private String externalProjectId;

    private String settings;

    private UUID gitConnectionId;

    private String cloneUrl;
}
