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
public class TaskSyncRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Team ID is required")
    private UUID teamId;

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotNull(message = "Platform connection ID is required")
    private UUID platformConnectionId;

    @NotBlank(message = "Project key is required")
    private String projectKey;
}
