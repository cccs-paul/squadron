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
public class TransitionRequest {

    @NotNull(message = "Task ID is required")
    private UUID taskId;

    @NotBlank(message = "Target state is required")
    private String targetState;

    private String reason;
}
