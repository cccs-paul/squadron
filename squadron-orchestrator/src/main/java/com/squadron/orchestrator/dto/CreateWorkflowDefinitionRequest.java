package com.squadron.orchestrator.dto;

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
public class CreateWorkflowDefinitionRequest {

    @NotNull
    private UUID tenantId;

    private UUID teamId;

    @NotBlank
    private String name;

    @NotBlank
    private String states;

    @NotBlank
    private String transitions;

    @Builder.Default
    private String hooks = "{}";
}
