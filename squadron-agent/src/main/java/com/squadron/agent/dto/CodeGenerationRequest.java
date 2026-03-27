package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for the code generation and PR creation workflow.
 * Contains all the information needed to create a branch, commit changes,
 * push, and open a pull request from a workspace container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationRequest {

    private UUID workspaceId;
    private UUID taskId;
    private UUID tenantId;
    private UUID projectId;
    private String taskTitle;
    private String platform;
    private String repoOwner;
    private String repoName;
    private String accessToken;
    private String commitMessage;
    private String prTitle;
    private String prDescription;
}
