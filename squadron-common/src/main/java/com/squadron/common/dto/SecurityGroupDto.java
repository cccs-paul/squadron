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
public class SecurityGroupDto {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private String accessLevel;        // "READ", "WRITE", "ADMIN"
    private List<UUID> memberUserIds;
    private List<UUID> memberTeamIds;
    private Instant createdAt;
    private Instant updatedAt;
}
