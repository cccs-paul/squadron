package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result DTO from the code generation and PR creation workflow.
 * Contains the outcome of the branch creation, commit, push, and PR creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationResult {

    private boolean success;
    private String branchName;
    private String prUrl;
    private String prId;
    private String error;
}
