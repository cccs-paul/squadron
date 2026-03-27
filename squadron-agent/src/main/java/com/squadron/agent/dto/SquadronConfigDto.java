package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquadronConfigDto {

    private UUID id;
    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private String name;
    private Map<String, Object> config;
}
