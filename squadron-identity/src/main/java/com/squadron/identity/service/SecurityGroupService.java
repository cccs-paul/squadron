package com.squadron.identity.service;

import com.squadron.common.dto.SecurityGroupDto;
import com.squadron.identity.entity.SecurityGroup;
import com.squadron.identity.entity.SecurityGroupMember;
import com.squadron.identity.entity.UserTeam;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.SecurityGroupMemberRepository;
import com.squadron.identity.repository.SecurityGroupRepository;
import com.squadron.identity.repository.UserTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for security group management including member management.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SecurityGroupService {

    private final SecurityGroupRepository securityGroupRepository;
    private final SecurityGroupMemberRepository securityGroupMemberRepository;
    private final UserTeamRepository userTeamRepository;

    public SecurityGroupDto createGroup(UUID tenantId, String name, String description, String accessLevel) {
        SecurityGroup group = SecurityGroup.builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .accessLevel(accessLevel != null ? accessLevel : "READ")
                .build();
        SecurityGroup saved = securityGroupRepository.save(group);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public SecurityGroupDto getGroup(UUID id) {
        SecurityGroup group = securityGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityGroup", "id", id));
        return toDtoWithMembers(group);
    }

    @Transactional(readOnly = true)
    public List<SecurityGroupDto> listGroups(UUID tenantId) {
        return securityGroupRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public SecurityGroupDto updateGroup(UUID id, String name, String description, String accessLevel) {
        SecurityGroup group = securityGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityGroup", "id", id));

        if (name != null) {
            group.setName(name);
        }
        if (description != null) {
            group.setDescription(description);
        }
        if (accessLevel != null) {
            group.setAccessLevel(accessLevel);
        }

        SecurityGroup saved = securityGroupRepository.save(group);
        return toDto(saved);
    }

    public void deleteGroup(UUID id) {
        if (!securityGroupRepository.existsById(id)) {
            throw new ResourceNotFoundException("SecurityGroup", "id", id);
        }
        securityGroupRepository.deleteById(id);
    }

    public SecurityGroupMember addMember(UUID groupId, String memberType, UUID memberId) {
        if (!securityGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("SecurityGroup", "id", groupId);
        }

        // Check if member already exists
        if (securityGroupMemberRepository.existsByGroupIdAndMemberTypeAndMemberId(groupId, memberType, memberId)) {
            throw new IllegalArgumentException("Member already exists in group");
        }

        SecurityGroupMember member = SecurityGroupMember.builder()
                .groupId(groupId)
                .memberType(memberType)
                .memberId(memberId)
                .build();
        return securityGroupMemberRepository.save(member);
    }

    public void removeMember(UUID groupId, String memberType, UUID memberId) {
        securityGroupMemberRepository.deleteByGroupIdAndMemberTypeAndMemberId(groupId, memberType, memberId);
    }

    @Transactional(readOnly = true)
    public List<SecurityGroupMember> getMembers(UUID groupId) {
        if (!securityGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("SecurityGroup", "id", groupId);
        }
        return securityGroupMemberRepository.findByGroupId(groupId);
    }

    /**
     * Get all security groups a user belongs to, both directly and via team membership.
     */
    @Transactional(readOnly = true)
    public List<SecurityGroup> getGroupsForUser(UUID userId) {
        // Direct membership
        List<SecurityGroupMember> directMemberships =
                securityGroupMemberRepository.findByMemberTypeAndMemberId("USER", userId);

        // Team-based membership
        List<UserTeam> userTeams = userTeamRepository.findByUserId(userId);
        List<SecurityGroupMember> teamMemberships = userTeams.stream()
                .flatMap(ut -> securityGroupMemberRepository
                        .findByMemberTypeAndMemberId("TEAM", ut.getTeamId()).stream())
                .toList();

        // Collect unique group IDs
        java.util.Set<UUID> groupIds = new java.util.HashSet<>();
        directMemberships.forEach(m -> groupIds.add(m.getGroupId()));
        teamMemberships.forEach(m -> groupIds.add(m.getGroupId()));

        return securityGroupRepository.findAllById(groupIds).stream().toList();
    }

    private SecurityGroupDto toDto(SecurityGroup group) {
        return SecurityGroupDto.builder()
                .id(group.getId())
                .tenantId(group.getTenantId())
                .name(group.getName())
                .description(group.getDescription())
                .accessLevel(group.getAccessLevel())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private SecurityGroupDto toDtoWithMembers(SecurityGroup group) {
        List<SecurityGroupMember> members = securityGroupMemberRepository.findByGroupId(group.getId());

        List<UUID> userIds = members.stream()
                .filter(m -> "USER".equals(m.getMemberType()))
                .map(SecurityGroupMember::getMemberId)
                .collect(Collectors.toList());

        List<UUID> teamIds = members.stream()
                .filter(m -> "TEAM".equals(m.getMemberType()))
                .map(SecurityGroupMember::getMemberId)
                .collect(Collectors.toList());

        return SecurityGroupDto.builder()
                .id(group.getId())
                .tenantId(group.getTenantId())
                .name(group.getName())
                .description(group.getDescription())
                .accessLevel(group.getAccessLevel())
                .memberUserIds(userIds)
                .memberTeamIds(teamIds)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}
