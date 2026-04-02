package com.squadron.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateWorkspaceRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID taskId;

    @NotNull
    private UUID userId;

    @NotBlank
    private String repoUrl;

    private String branch;

    private String baseImage;

    private Map<String, Object> resourceLimits;

    private String accessToken;

    private String sshPrivateKey;
}
