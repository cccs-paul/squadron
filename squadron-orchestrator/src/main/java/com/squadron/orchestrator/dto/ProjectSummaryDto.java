package com.squadron.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Summary DTO for project list view -- includes task counts, sync status, and workflow mapping info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryDto {
    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private String name;
    private String description;
    private String repositoryUrl;
    private String defaultBranch;
    private String branchNamingTemplate;

    /** Platform connection ID for ticket provider */
    private UUID connectionId;
    /** External project key on the ticket provider (e.g., "PE" in JIRA) */
    private String externalProjectId;
    /** Git remote connection ID */
    private UUID gitConnectionId;

    /** Total number of tasks in this project */
    private long totalTasks;
    /** Number of active tasks (not DONE or BACKLOG) */
    private long activeTasks;
    /** Number of tasks by state */
    private Map<String, Long> taskCountsByState;

    /** Whether workflow mappings are configured */
    private boolean workflowMappingsConfigured;
    /** Number of configured workflow state mappings */
    private int workflowMappingCount;

    private String createdAt;
    private String updatedAt;
}
