package com.squadron.common.dto;

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
public class TaskPlanDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private UUID conversationId;
    private Integer version;
    private String planContent;
    private String status;
    private UUID approvedBy;
    private Instant approvedAt;
    private Instant createdAt;
}
