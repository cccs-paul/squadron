package com.squadron.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a ticketless task — a task created directly from
 * the UI without an external ticket (JIRA, GitHub Issues, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketlessTaskRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotBlank(message = "Prompt is required")
    private String prompt;

    /** Target branch name (existing or new) */
    @NotBlank(message = "Branch name is required")
    private String branchName;

    /** Whether to create a new branch or use an existing one */
    private boolean createBranch;

    /** Agent mode: PLAN or BUILD */
    @NotBlank(message = "Agent mode is required")
    private String agentMode;

    /** UUID of the agent config to use */
    @NotNull(message = "Agent config ID is required")
    private UUID agentConfigId;

    /** Optional project ID to associate the task with */
    private UUID projectId;

    /** Optional title — auto-generated from prompt if not provided */
    private String title;

    /** Optional priority */
    private String priority;
}
