package com.squadron.agent.dto;

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
public class PlanApprovalRequest {

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    private boolean approved;
}
