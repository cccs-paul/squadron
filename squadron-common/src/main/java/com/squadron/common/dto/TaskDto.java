package com.squadron.common.dto;

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
public class TaskDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private UUID projectId;
    private String externalId;
    private String externalUrl;
    private String title;
    private String description;
    private UUID assigneeId;
    private String priority;
    private List<String> labels;
    private Instant createdAt;
    private Instant updatedAt;
}
