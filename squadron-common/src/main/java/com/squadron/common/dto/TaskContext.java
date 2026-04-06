package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Enriched task context that contains all metadata agents need to perform their work.
 * Populated by the orchestrator when publishing state change events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskContext {

    private UUID taskId;
    private UUID tenantId;
    private UUID projectId;
    /** The user who triggered the state transition (or the assignee) */
    private UUID userId;
    /** Platform connection ID (from Project entity) */
    private UUID connectionId;
    /** Git repository URL (from Project entity) */
    private String repoUrl;
    /** Default branch name, e.g. "main" (from Project entity) */
    private String defaultBranch;
    /** Branch strategy type, e.g. "TRUNK_BASED" (from Project entity) */
    private String branchStrategy;
    /** Branch naming template, e.g. "{prefix}{taskId}/{slug}" (from Project entity) */
    private String branchNamingTemplate;
    /** External ticket ID from the ticketing platform */
    private String externalId;
    /** Task title */
    private String title;
}
