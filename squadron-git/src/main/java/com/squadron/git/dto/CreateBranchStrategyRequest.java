package com.squadron.git.dto;

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
public class CreateBranchStrategyRequest {

    @NotNull
    private UUID tenantId;

    private UUID projectId;

    @NotBlank
    private String strategyType;

    private String branchPrefix;

    @NotBlank
    private String targetBranch;

    private String developmentBranch;

    private String branchNameTemplate;
}
