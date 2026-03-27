package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGroupDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UUID> memberUserIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> memberTeamIds = List.of(UUID.randomUUID());
        Instant now = Instant.now();

        SecurityGroupDto dto = SecurityGroupDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Backend Team")
                .description("Access group for backend developers")
                .accessLevel("WRITE")
                .memberUserIds(memberUserIds)
                .memberTeamIds(memberTeamIds)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Backend Team", dto.getName());
        assertEquals("Access group for backend developers", dto.getDescription());
        assertEquals("WRITE", dto.getAccessLevel());
        assertEquals(memberUserIds, dto.getMemberUserIds());
        assertEquals(memberTeamIds, dto.getMemberTeamIds());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        SecurityGroupDto dto = new SecurityGroupDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getDescription());
        assertNull(dto.getAccessLevel());
        assertNull(dto.getMemberUserIds());
        assertNull(dto.getMemberTeamIds());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<UUID> memberUserIds = List.of(UUID.randomUUID());
        List<UUID> memberTeamIds = List.of(UUID.randomUUID());
        Instant created = Instant.now();
        Instant updated = Instant.now();

        SecurityGroupDto dto = new SecurityGroupDto(
                id, tenantId, "Admins", "Administrator group",
                "ADMIN", memberUserIds, memberTeamIds, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Admins", dto.getName());
        assertEquals("Administrator group", dto.getDescription());
        assertEquals("ADMIN", dto.getAccessLevel());
        assertEquals(memberUserIds, dto.getMemberUserIds());
        assertEquals(memberTeamIds, dto.getMemberTeamIds());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        SecurityGroupDto dto = new SecurityGroupDto();
        UUID id = UUID.randomUUID();
        List<UUID> userIds = List.of(UUID.randomUUID());
        dto.setId(id);
        dto.setName("Readers");
        dto.setAccessLevel("READ");
        dto.setMemberUserIds(userIds);

        assertEquals(id, dto.getId());
        assertEquals("Readers", dto.getName());
        assertEquals("READ", dto.getAccessLevel());
        assertEquals(userIds, dto.getMemberUserIds());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        SecurityGroupDto dto1 = SecurityGroupDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Group A")
                .accessLevel("READ")
                .build();

        SecurityGroupDto dto2 = SecurityGroupDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Group A")
                .accessLevel("READ")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        SecurityGroupDto dto1 = SecurityGroupDto.builder()
                .name("Group A")
                .build();

        SecurityGroupDto dto2 = SecurityGroupDto.builder()
                .name("Group B")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        SecurityGroupDto dto = SecurityGroupDto.builder()
                .name("DevOps")
                .accessLevel("ADMIN")
                .description("DevOps team group")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("DevOps"));
        assertTrue(str.contains("ADMIN"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        SecurityGroupDto dto = SecurityGroupDto.builder()
                .id(null)
                .tenantId(null)
                .name(null)
                .description(null)
                .accessLevel(null)
                .memberUserIds(null)
                .memberTeamIds(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getDescription());
        assertNull(dto.getAccessLevel());
        assertNull(dto.getMemberUserIds());
        assertNull(dto.getMemberTeamIds());
    }

    @Test
    void should_handleEmptyLists_when_emptyListsProvided() {
        SecurityGroupDto dto = SecurityGroupDto.builder()
                .memberUserIds(Collections.emptyList())
                .memberTeamIds(Collections.emptyList())
                .build();

        assertNotNull(dto.getMemberUserIds());
        assertTrue(dto.getMemberUserIds().isEmpty());
        assertNotNull(dto.getMemberTeamIds());
        assertTrue(dto.getMemberTeamIds().isEmpty());
    }
}
