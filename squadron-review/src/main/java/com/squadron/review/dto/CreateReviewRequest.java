package com.squadron.review.dto;

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
public class CreateReviewRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID taskId;

    private UUID reviewerId;

    @NotBlank
    private String reviewerType;
}
