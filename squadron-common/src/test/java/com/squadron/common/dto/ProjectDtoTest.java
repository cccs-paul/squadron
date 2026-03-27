package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Map<String, Object> settings = Map.of("autoReview", true);
        Instant now = Instant.now();

        ProjectDto dto = ProjectDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("squadron-backend")
                .connectionId(connectionId)
                .externalProjectId("PROJ-123")
                .repoUrl("https://github.com/org/repo")
                .defaultBranch("main")
                .branchStrategy("feature-branch")
                .settings(settings)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals("squadron-backend", dto.getName());
        assertEquals(connectionId, dto.getConnectionId());
        assertEquals("PROJ-123", dto.getExternalProjectId());
        assertEquals("https://github.com/org/repo", dto.getRepoUrl());
        assertEquals("main", dto.getDefaultBranch());
        assertEquals("feature-branch", dto.getBranchStrategy());
        assertEquals(settings, dto.getSettings());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ProjectDto dto = new ProjectDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTeamId());
        assertNull(dto.getName());
        assertNull(dto.getConnectionId());
        assertNull(dto.getExternalProjectId());
        assertNull(dto.getRepoUrl());
        assertNull(dto.getDefaultBranch());
        assertNull(dto.getBranchStrategy());
        assertNull(dto.getSettings());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Map<String, Object> settings = Map.of("ci", "github-actions");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        ProjectDto dto = new ProjectDto(
                id, tenantId, teamId, "my-project", connectionId,
                "EXT-1", "https://github.com/o/r", "develop",
                "gitflow", settings, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals("my-project", dto.getName());
        assertEquals(connectionId, dto.getConnectionId());
        assertEquals("EXT-1", dto.getExternalProjectId());
        assertEquals("https://github.com/o/r", dto.getRepoUrl());
        assertEquals("develop", dto.getDefaultBranch());
        assertEquals("gitflow", dto.getBranchStrategy());
        assertEquals(settings, dto.getSettings());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ProjectDto dto = new ProjectDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setName("updated-project");
        dto.setDefaultBranch("main");
        dto.setBranchStrategy("trunk-based");

        assertEquals(id, dto.getId());
        assertEquals("updated-project", dto.getName());
        assertEquals("main", dto.getDefaultBranch());
        assertEquals("trunk-based", dto.getBranchStrategy());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ProjectDto dto1 = ProjectDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("project")
                .build();

        ProjectDto dto2 = ProjectDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("project")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ProjectDto dto1 = ProjectDto.builder()
                .name("project-1")
                .build();

        ProjectDto dto2 = ProjectDto.builder()
                .name("project-2")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        ProjectDto dto = ProjectDto.builder()
                .name("squadron-backend")
                .repoUrl("https://github.com/org/repo")
                .defaultBranch("main")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("squadron-backend"));
        assertTrue(str.contains("https://github.com/org/repo"));
        assertTrue(str.contains("main"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        ProjectDto dto = ProjectDto.builder()
                .id(null)
                .tenantId(null)
                .name(null)
                .settings(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getSettings());
    }

    @Test
    void should_handleEmptySettings_when_emptyMapProvided() {
        ProjectDto dto = ProjectDto.builder()
                .settings(Map.of())
                .build();

        assertNotNull(dto.getSettings());
        assertTrue(dto.getSettings().isEmpty());
    }
}
