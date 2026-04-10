package com.squadron.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Detailed task DTO with workflow state, transition history, and project context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailDto {
    private UUID id;
    private UUID tenantId;
    private UUID projectId;
    private UUID teamId;
    private UUID assigneeId;
    private String title;
    private String description;
    private String externalId;
    private String externalUrl;
    private String priority;
    private List<String> labels;
    private long tokenUsage;

    /** Current workflow state */
    private String currentState;
    /** Previous workflow state */
    private String previousState;
    /** When the last transition occurred */
    private String lastTransitionAt;
    /** Available transitions from the current state */
    private List<String> availableTransitions;

    /** Project name for display */
    private String projectName;
    /** External status mapped from the current internal state (via project workflow mappings) */
    private String mappedExternalStatus;

    private String createdAt;
    private String updatedAt;
}
