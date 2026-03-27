package com.squadron.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePermissionDto {
    private UUID id;
    private UUID tenantId;
    private String resourceType;       // "PROJECT", "TASK", "REPOSITORY", "CONFIGURATION"
    private UUID resourceId;
    private String granteeType;        // "USER", "TEAM", "SECURITY_GROUP"
    private UUID granteeId;
    private String accessLevel;        // "READ", "WRITE", "ADMIN"
}
