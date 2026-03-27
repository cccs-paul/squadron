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
public class WorkflowDefinitionDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private String name;
    private String states;
    private String transitions;
    private String hooks;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
