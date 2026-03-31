package com.squadron.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a single workflow state mapping.  Used both as input (save) and
 * output (get) for the project workflow mappings API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMappingDto {

    @NotBlank(message = "Internal state is required")
    private String internalState;

    @NotBlank(message = "External status is required")
    private String externalStatus;
}
