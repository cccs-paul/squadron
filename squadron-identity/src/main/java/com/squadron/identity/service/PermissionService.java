package com.squadron.identity.service;

import com.squadron.common.dto.ResourcePermissionDto;
import com.squadron.common.security.AccessLevel;
import com.squadron.identity.entity.ResourcePermission;
import com.squadron.identity.entity.SecurityGroup;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.ResourcePermissionRepository;
import com.squadron.identity.repository.SecurityGroupMemberRepository;
import com.squadron.identity.repository.SecurityGroupRepository;
import com.squadron.identity.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for resource permission management.
 * Supports hierarchical access levels: ADMIN > WRITE > READ.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final ResourcePermissionRepository permissionRepository;
    private final SecurityGroupMemberRepository securityGroupMemberRepository;
    private final SecurityGroupRepository securityGroupRepository;
    private final UserTeamRepository userTeamRepository;

    public ResourcePermissionDto grantPermission(UUID tenantId, String resourceType, UUID resourceId,
                                                  String granteeType, UUID granteeId, String accessLevel) {
        ResourcePermission permission = ResourcePermission.builder()
                .tenantId(tenantId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .granteeType(granteeType)
                .granteeId(granteeId)
                .accessLevel(accessLevel != null ? accessLevel : "READ")
                .build();
        ResourcePermission saved = permissionRepository.save(permission);
        return toDto(saved);
    }

    public void revokePermission(UUID permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new ResourceNotFoundException("ResourcePermission", "id", permissionId);
        }
        permissionRepository.deleteById(permissionId);
    }

    @Transactional(readOnly = true)
    public List<ResourcePermissionDto> getPermissions(UUID tenantId, String resourceType, UUID resourceId) {
        return permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, resourceType, resourceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Check if a user has the required access level on a resource.
     * Checks direct user permissions, team-based permissions, and security group permissions.
     * ADMIN includes WRITE includes READ (hierarchical).
     */
    @Transactional(readOnly = true)
    public boolean checkAccess(UUID userId, UUID tenantId, String resourceType, UUID resourceId,
                               AccessLevel requiredLevel) {
        AccessLevel effectiveLevel = getEffectiveAccessLevel(userId, tenantId, resourceType, resourceId);
        return effectiveLevel.ordinal() >= requiredLevel.ordinal();
    }

    /**
     * Returns the highest access level the user has through any grant path.
     */
    @Transactional(readOnly = true)
    public AccessLevel getEffectiveAccessLevel(UUID userId, UUID tenantId, String resourceType, UUID resourceId) {
        List<ResourcePermission> allPermissions = new ArrayList<>();

        // 1. Direct user permissions
        List<ResourcePermission> directPerms = permissionRepository
                .findByTenantIdAndResourceTypeAndResourceId(tenantId, resourceType, resourceId)
                .stream()
                .filter(p -> "USER".equals(p.getGranteeType()) && userId.equals(p.getGranteeId()))
                .toList();
        allPermissions.addAll(directPerms);

        // 2. Team-based permissions
        List<UserTeam> userTeams = userTeamRepository.findByUserId(userId);
        for (UserTeam userTeam : userTeams) {
            List<ResourcePermission> teamPerms = permissionRepository
                    .findByTenantIdAndResourceTypeAndResourceId(tenantId, resourceType, resourceId)
                    .stream()
                    .filter(p -> "TEAM".equals(p.getGranteeType()) && userTeam.getTeamId().equals(p.getGranteeId()))
                    .toList();
            allPermissions.addAll(teamPerms);
        }

        // 3. Security group permissions
        // Get groups the user belongs to directly
        List<SecurityGroupMember> directGroupMemberships =
                securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId);

        // Get groups via team membership
        List<SecurityGroupMember> teamGroupMemberships = userTeams.stream()
                .flatMap(ut -> securityGroupMemberRepository
                        .findByMemberTypeAndMemberId("TEAM", ut.getTeamId()).stream())
                .toList();

        // Collect all unique group IDs
        java.util.Set<UUID> groupIds = new java.util.HashSet<>();
        directGroupMemberships.forEach(m -> groupIds.add(m.getGroupId()));
        teamGroupMemberships.forEach(m -> groupIds.add(m.getGroupId()));

        for (UUID groupId : groupIds) {
            List<ResourcePermission> groupPerms = permissionRepository
                    .findByTenantIdAndResourceTypeAndResourceId(tenantId, resourceType, resourceId)
                    .stream()
                    .filter(p -> "SECURITY_GROUP".equals(p.getGranteeType()) && groupId.equals(p.getGranteeId()))
                    .toList();
            allPermissions.addAll(groupPerms);

            // Also consider the security group's default access level
            securityGroupRepository.findById(groupId).ifPresent(group -> {
                // The group's default access level applies to resources where the group has explicit permissions
                // We don't add it as a blanket permission
            });
        }

        // Find the highest access level
        AccessLevel highest = AccessLevel.NONE;
        for (ResourcePermission perm : allPermissions) {
            try {
                AccessLevel level = AccessLevel.valueOf(perm.getAccessLevel());
                if (level.ordinal() > highest.ordinal()) {
                    highest = level;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid access level in permission {}: {}", perm.getId(), perm.getAccessLevel());
            }
        }

        return highest;
    }

    private ResourcePermissionDto toDto(ResourcePermission permission) {
        return ResourcePermissionDto.builder()
                .id(permission.getId())
                .tenantId(permission.getTenantId())
                .resourceType(permission.getResourceType())
                .resourceId(permission.getResourceId())
                .granteeType(permission.getGranteeType())
                .granteeId(permission.getGranteeId())
                .accessLevel(permission.getAccessLevel())
                .build();
    }
}
