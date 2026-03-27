package com.squadron.identity.service;

import com.squadron.common.dto.SecurityGroupDto;
import com.squadron.identity.entity.SecurityGroup;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.exception.ResourceNotFoundException;
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
class SecurityGroupServiceTest {

    @Mock
    private SecurityGroupRepository securityGroupRepository;
    @Mock
    private SecurityGroupMemberRepository securityGroupMemberRepository;
    @Mock
    private UserTeamRepository userTeamRepository;

    @InjectMocks
    private SecurityGroupService securityGroupService;

    private UUID groupId;
    private UUID tenantId;
    private SecurityGroup group;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        group = SecurityGroup.builder()
                .id(groupId)
                .tenantId(tenantId)
                .name("Engineering")
                .description("Engineering team group")
                .accessLevel("WRITE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void should_createGroup_when_validInput() {
        when(securityGroupRepository.save(any(SecurityGroup.class))).thenReturn(group);

        SecurityGroupDto result = securityGroupService.createGroup(tenantId, "Engineering", "desc", "WRITE");

        assertNotNull(result);
        assertEquals("Engineering", result.getName());
        assertEquals("WRITE", result.getAccessLevel());
    }

    @Test
    void should_createGroupWithDefaultAccessLevel_when_accessLevelIsNull() {
        when(securityGroupRepository.save(any(SecurityGroup.class))).thenReturn(group);

        securityGroupService.createGroup(tenantId, "Readers", "desc", null);

        verify(securityGroupRepository).save(argThat(g -> "READ".equals(g.getAccessLevel())));
    }

    @Test
    void should_getGroupWithMembers_when_exists() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        when(securityGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(securityGroupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(
                SecurityGroupMember.builder().groupId(groupId).memberType("USER").memberId(userId).build(),
                SecurityGroupMember.builder().groupId(groupId).memberType("TEAM").memberId(teamId).build()
        ));

        SecurityGroupDto result = securityGroupService.getGroup(groupId);

        assertNotNull(result);
        assertEquals(1, result.getMemberUserIds().size());
        assertEquals(1, result.getMemberTeamIds().size());
        assertTrue(result.getMemberUserIds().contains(userId));
        assertTrue(result.getMemberTeamIds().contains(teamId));
    }

    @Test
    void should_throwNotFound_when_groupDoesNotExist() {
        when(securityGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> securityGroupService.getGroup(groupId));
    }

    @Test
    void should_listGroups_when_called() {
        when(securityGroupRepository.findByTenantId(tenantId)).thenReturn(List.of(group));

        List<SecurityGroupDto> results = securityGroupService.listGroups(tenantId);

        assertEquals(1, results.size());
    }

    @Test
    void should_updateGroup_when_exists() {
        when(securityGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(securityGroupRepository.save(group)).thenReturn(group);

        SecurityGroupDto result = securityGroupService.updateGroup(groupId, "Updated", "new desc", "ADMIN");

        assertEquals("Updated", group.getName());
        assertEquals("new desc", group.getDescription());
        assertEquals("ADMIN", group.getAccessLevel());
    }

    @Test
    void should_updateOnlyProvidedFields_when_someFieldsAreNull() {
        when(securityGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(securityGroupRepository.save(group)).thenReturn(group);

        securityGroupService.updateGroup(groupId, null, null, "ADMIN");

        assertEquals("Engineering", group.getName()); // unchanged
        assertEquals("ADMIN", group.getAccessLevel());
    }

    @Test
    void should_throwNotFound_when_updatingNonExistentGroup() {
        when(securityGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                securityGroupService.updateGroup(groupId, "name", "desc", "READ"));
    }

    @Test
    void should_deleteGroup_when_exists() {
        when(securityGroupRepository.existsById(groupId)).thenReturn(true);

        securityGroupService.deleteGroup(groupId);

        verify(securityGroupRepository).deleteById(groupId);
    }

    @Test
    void should_throwNotFound_when_deletingNonExistentGroup() {
        when(securityGroupRepository.existsById(groupId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> securityGroupService.deleteGroup(groupId));
    }

    @Test
    void should_addMember_when_groupExistsAndMemberNotPresent() {
        UUID memberId = UUID.randomUUID();
        when(securityGroupRepository.existsById(groupId)).thenReturn(true);
        when(securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(groupId, "USER", memberId))
                .thenReturn(false);
        SecurityGroupMember savedMember = SecurityGroupMember.builder()
                .id(UUID.randomUUID()).groupId(groupId).memberType("USER").memberId(memberId).build();
        when(securityGroupMemberRepository.save(any(SecurityGroupMember.class))).thenReturn(savedMember);

        SecurityGroupMember result = securityGroupService.addMember(groupId, "USER", memberId);

        assertNotNull(result);
        verify(securityGroupMemberRepository).save(any(SecurityGroupMember.class));
    }

    @Test
    void should_throwIllegalArgument_when_memberAlreadyExists() {
        UUID memberId = UUID.randomUUID();
        when(securityGroupRepository.existsById(groupId)).thenReturn(true);
        when(securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(groupId, "USER", memberId))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                securityGroupService.addMember(groupId, "USER", memberId));
    }

    @Test
    void should_throwNotFound_when_addingMemberToNonExistentGroup() {
        when(securityGroupRepository.existsById(groupId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                securityGroupService.addMember(groupId, "USER", UUID.randomUUID()));
    }

    @Test
    void should_removeMember_when_called() {
        UUID memberId = UUID.randomUUID();

        securityGroupService.removeMember(groupId, "USER", memberId);

        verify(securityGroupMemberRepository).deleteByGroupIdAndMemberTypeAndMemberId(groupId, "USER", memberId);
    }

    @Test
    void should_getMembers_when_groupExists() {
        when(securityGroupRepository.existsById(groupId)).thenReturn(true);
        when(securityGroupMemberRepository.findByGroupId(groupId)).thenReturn(List.of(
                SecurityGroupMember.builder().groupId(groupId).memberType("USER").memberId(UUID.randomUUID()).build()
        ));

        List<SecurityGroupMember> members = securityGroupService.getMembers(groupId);

        assertEquals(1, members.size());
    }

    @Test
    void should_throwNotFound_when_getMembersForNonExistentGroup() {
        when(securityGroupRepository.existsById(groupId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> securityGroupService.getMembers(groupId));
    }

    @Test
    void should_getGroupsForUser_when_directAndTeamMembership() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID group2Id = UUID.randomUUID();

        SecurityGroup group2 = SecurityGroup.builder()
                .id(group2Id).tenantId(tenantId).name("QA").accessLevel("READ")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId))
                .thenReturn(List.of(
                        SecurityGroupMember.builder().groupId(groupId).memberType("USER").memberId(userId).build()
                ));
        when(userTeamRepository.findByUserId(userId))
                .thenReturn(List.of(UserTeam.builder().userId(userId).teamId(teamId).role("MEMBER").build()));
        when(securityGroupMemberRepository.findByMemberTypeAndMemberId("TEAM", teamId))
                .thenReturn(List.of(
                        SecurityGroupMember.builder().groupId(group2Id).memberType("TEAM").memberId(teamId).build()
                ));
        when(securityGroupRepository.findAllById(any())).thenReturn(List.of(group, group2));

        List<SecurityGroup> groups = securityGroupService.getGroupsForUser(userId);

        assertEquals(2, groups.size());
    }
}
