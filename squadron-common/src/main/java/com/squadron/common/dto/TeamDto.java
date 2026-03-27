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
public class TeamDto {

    private UUID id;
    private UUID tenantId;
    private String name;
    private Map<String, Object> settings;
    private Instant createdAt;
    private Instant updatedAt;
}
