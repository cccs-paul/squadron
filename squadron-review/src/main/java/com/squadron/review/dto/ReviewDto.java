package com.squadron.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private UUID reviewerId;
    private String reviewerType;
    private String status;
    private String summary;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ReviewCommentDto> comments;
}
