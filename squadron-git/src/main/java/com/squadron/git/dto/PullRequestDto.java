package com.squadron.git.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private String platform;
    private String externalPrId;
    private String externalPrUrl;
    private String title;
    private String sourceBranch;
    private String targetBranch;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
