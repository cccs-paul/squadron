package com.squadron.orchestrator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "task_workflows")
public class TaskWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "task_id", nullable = false, unique = true)
    private UUID taskId;

    @Column(name = "current_state", nullable = false)
    private String currentState;

    @Column(name = "previous_state")
    private String previousState;

    @Column(name = "transition_at", nullable = false)
    private Instant transitionAt;

    @Column(name = "transitioned_by", nullable = false)
    private UUID transitionedBy;

    @Column(columnDefinition = "jsonb")
    private String metadata;
}
