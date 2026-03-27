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
public class BranchRequest {

    @NotNull
    private UUID workspaceId;

    @NotBlank
    private String branchName;

    private String baseBranch;
}
