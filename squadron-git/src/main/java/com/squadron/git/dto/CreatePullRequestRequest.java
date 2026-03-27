package com.squadron.git.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePullRequestRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID taskId;

    @NotBlank
    private String platform;

    private String repoOwner;

    private String repoName;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String sourceBranch;

    @NotBlank
    private String targetBranch;

    private List<String> reviewers;

    private String accessToken;
}
