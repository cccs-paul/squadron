package com.squadron.orchestrator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps a Squadron internal workflow state (e.g. REVIEW) to the corresponding
 * status name used on the external ticketing platform for a specific project
 * (e.g. "Code Review" in JIRA).
 *
 * A project may have 0..N mappings (one per internal state that has a
 * corresponding step on the external platform).  States that are not mapped
 * are assumed to have no equivalent on the external board.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_workflow_mappings",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_project_internal_state",
               columnNames = {"project_id", "internal_state"}))
public class ProjectWorkflowMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * One of the Squadron TaskState enum values (e.g. BACKLOG, PLANNING,
     * PROPOSE_CODE, REVIEW, QA, MERGE, DONE).
     */
    @Column(name = "internal_state", nullable = false, length = 50)
    private String internalState;

    /**
     * The status name on the external platform (e.g. "In Progress",
     * "Code Review", "Done").  This is the value that will be pushed to the
     * ticketing system and matched when pulling statuses.
     */
    @Column(name = "external_status", nullable = false, length = 255)
    private String externalStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
