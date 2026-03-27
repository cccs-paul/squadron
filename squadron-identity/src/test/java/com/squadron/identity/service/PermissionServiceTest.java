package com.squadron.identity.service;

import com.squadron.common.dto.ResourcePermissionDto;
import com.squadron.common.security.AccessLevel;
import com.squadron.identity.entity.ResourcePermission;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.ResourcePermissionRepository;
import com.squadron.identity.repository.SecurityGroupMemberRepository;
import com.squadron.identity.repository.SecurityGroupRepository;
import com.squadron.identity.repository.UserTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private ResourcePermissionRepository permissionRepository;
    @Mock
    private SecurityGroupMemberRepository securityGroupMemberRepository;
    @Mock
    private SecurityGroupRepository securityGroupRepository;
    @Mock
    private UserTeamRepository userTeamRepository;

    @InjectMocks
    private PermissionService permissionService;

    private UUID tenantId;
    private UUID userId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    @Test
    void should_grantPermission_when_validInput() {
        ResourcePermission saved = ResourcePermission.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .resourceType("PROJECT")
                .resourceId(resourceId)
                .granteeType("USER")
                .granteeId(userId)
                .accessLevel("WRITE")
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.save(any(ResourcePermission.class))).thenReturn(saved);

        ResourcePermissionDto result = permissionService.grantPermission(
                tenantId, "PROJECT", resourceId, "USER", userId, "WRITE");

        assertNotNull(result);
        assertEquals("WRITE", result.getAccessLevel());
        assertEquals("USER", result.getGranteeType());
    }

    @Test
    void should_useDefaultAccessLevel_when_accessLevelIsNull() {
        ResourcePermission saved = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("READ").createdAt(Instant.now()).build();

        when(permissionRepository.save(any(ResourcePermission.class))).thenReturn(saved);

        permissionService.grantPermission(tenantId, "PROJECT", resourceId, "USER", userId, null);

        verify(permissionRepository).save(argThat(p -> "READ".equals(p.getAccessLevel())));
    }

    @Test
    void should_revokePermission_when_exists() {
        UUID permId = UUID.randomUUID();
        when(permissionRepository.existsById(permId)).thenReturn(true);

        permissionService.revokePermission(permId);

        verify(permissionRepository).deleteById(permId);
    }

    @Test
    void should_throwNotFound_when_revokingNonExistentPermission() {
        UUID permId = UUID.randomUUID();
        when(permissionRepository.existsById(permId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> permissionService.revokePermission(permId));
    }

    @Test
    void should_getPermissions_when_called() {
        ResourcePermission perm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("READ").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(perm));

        List<ResourcePermissionDto> results = permissionService.getPermissions(tenantId, "PROJECT", resourceId);

        assertEquals(1, results.size());
    }

    @Test
    void should_returnTrue_when_userHasDirectAdminAndAdminRequired() {
        ResourcePermission perm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("ADMIN").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(perm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of());
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        assertTrue(permissionService.checkAccess(userId, tenantId, "PROJECT", resourceId, AccessLevel.ADMIN));
    }

    @Test
    void should_returnTrue_when_userHasWriteAndReadRequired() {
        ResourcePermission perm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("WRITE").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(perm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of());
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        assertTrue(permissionService.checkAccess(userId, tenantId, "PROJECT", resourceId, AccessLevel.READ));
    }

    @Test
    void should_returnFalse_when_userHasReadAndWriteRequired() {
        ResourcePermission perm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("READ").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(perm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of());
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        assertFalse(permissionService.checkAccess(userId, tenantId, "PROJECT", resourceId, AccessLevel.WRITE));
    }

    @Test
    void should_returnNone_when_noPermissionsExist() {
        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of());
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of());
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        AccessLevel effective = permissionService.getEffectiveAccessLevel(userId, tenantId, "PROJECT", resourceId);

        assertEquals(AccessLevel.NONE, effective);
    }

    @Test
    void should_considerTeamPermissions_when_userIsTeamMember() {
        UUID teamId = UUID.randomUUID();

        ResourcePermission teamPerm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("TEAM").granteeId(teamId)
                .accessLevel("WRITE").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(teamPerm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of(
                UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build()
        ));
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        AccessLevel effective = permissionService.getEffectiveAccessLevel(userId, tenantId, "PROJECT", resourceId);

        assertEquals(AccessLevel.WRITE, effective);
    }

    @Test
    void should_considerSecurityGroupPermissions_when_userIsGroupMember() {
        UUID sgroupId = UUID.randomUUID();

        ResourcePermission sgPerm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("SECURITY_GROUP").granteeId(sgroupId)
                .accessLevel("ADMIN").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(sgPerm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of());
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of(
                SecurityGroupMember.builder().groupId(sgroupId).memberType("USER").memberId(userId).build()
        ));
        when(securityGroupRepository.findById(sgroupId)).thenReturn(Optional.empty());

        AccessLevel effective = permissionService.getEffectiveAccessLevel(userId, tenantId, "PROJECT", resourceId);

        assertEquals(AccessLevel.ADMIN, effective);
    }

    @Test
    void should_returnHighestAccessLevel_when_multiplePermissionsExist() {
        ResourcePermission readPerm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("USER").granteeId(userId)
                .accessLevel("READ").createdAt(Instant.now()).build();

        UUID teamId = UUID.randomUUID();
        ResourcePermission writePerm = ResourcePermission.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).resourceType("PROJECT")
                .resourceId(resourceId).granteeType("TEAM").granteeId(teamId)
                .accessLevel("WRITE").createdAt(Instant.now()).build();

        when(permissionRepository.findByTenantIdAndResourceTypeAndResourceId(tenantId, "PROJECT", resourceId))
                .thenReturn(List.of(readPerm, writePerm));
        when(userTeamRepository.findByUserId(userId)).thenReturn(List.of(
                UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build()
        ));
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId)).thenReturn(List.of());

        AccessLevel effective = permissionService.getEffectiveAccessLevel(userId, tenantId, "PROJECT", resourceId);

        assertEquals(AccessLevel.WRITE, effective);
    }
}
