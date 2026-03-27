package com.squadron.review.dto;

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
public class ReviewPolicyDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private Integer minHumanApprovals;
    private Boolean requireAiReview;
    private Boolean selfReviewAllowed;
    private String autoRequestReviewers;
    private String reviewChecklist;
    private Instant createdAt;
    private Instant updatedAt;
}
