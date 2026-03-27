package com.squadron.orchestrator.dto;

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
public class TaskWorkflowDto {

    private UUID taskId;
    private String currentState;
    private String previousState;
    private Instant transitionAt;
    private UUID transitionedBy;
    private String metadata;
}
