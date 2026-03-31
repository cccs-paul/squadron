package com.squadron.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for bulk-saving the workflow step mappings for a project.
 * The list replaces all existing mappings for that project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectWorkflowMappingsRequest {

    @NotNull(message = "Mappings list is required")
    @Valid
    private List<WorkflowMappingDto> mappings;
}
