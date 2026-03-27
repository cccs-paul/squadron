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
public class BranchStrategyDto {

    private UUID id;
    private UUID tenantId;
    private UUID projectId;
    private String strategyType;
    private String branchPrefix;
    private String targetBranch;
    private String developmentBranch;
    private String branchNameTemplate;
    private Instant createdAt;
}
