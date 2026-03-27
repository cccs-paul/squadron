package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTaskDto {

    private String externalId;
    private String externalUrl;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String assignee;
    private List<String> labels;
    private Instant createdAt;
    private Instant updatedAt;
}
