package com.squadron.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummary {

    private UUID taskId;
    private int totalReviews;
    private int humanApprovals;
    private boolean aiApproval;
    private boolean policyMet;
    private List<ReviewDto> reviews;
}
