package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
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
    private Map<String, Object> comments;
    private Instant createdAt;
    private Instant updatedAt;
}
