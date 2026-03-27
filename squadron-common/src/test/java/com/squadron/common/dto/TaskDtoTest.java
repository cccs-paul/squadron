package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        List<String> labels = List.of("bug", "high-priority");
        Instant now = Instant.now();

        TaskDto dto = TaskDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("JIRA-123")
                .externalUrl("https://jira.example.com/browse/JIRA-123")
                .title("Fix authentication bug")
                .description("Users cannot log in with OIDC provider")
                .assigneeId(assigneeId)
                .priority("HIGH")
                .labels(labels)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals("JIRA-123", dto.getExternalId());
        assertEquals("https://jira.example.com/browse/JIRA-123", dto.getExternalUrl());
        assertEquals("Fix authentication bug", dto.getTitle());
        assertEquals("Users cannot log in with OIDC provider", dto.getDescription());
        assertEquals(assigneeId, dto.getAssigneeId());
        assertEquals("HIGH", dto.getPriority());
        assertEquals(labels, dto.getLabels());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        TaskDto dto = new TaskDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTeamId());
        assertNull(dto.getProjectId());
        assertNull(dto.getExternalId());
        assertNull(dto.getExternalUrl());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
        assertNull(dto.getAssigneeId());
        assertNull(dto.getPriority());
        assertNull(dto.getLabels());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        List<String> labels = List.of("feature");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        TaskDto dto = new TaskDto(
                id, tenantId, teamId, projectId, "GH-456",
                "https://github.com/org/repo/issues/456",
                "Add search feature", "Implement full-text search",
                assigneeId, "MEDIUM", labels, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals("GH-456", dto.getExternalId());
        assertEquals("https://github.com/org/repo/issues/456", dto.getExternalUrl());
        assertEquals("Add search feature", dto.getTitle());
        assertEquals("Implement full-text search", dto.getDescription());
        assertEquals(assigneeId, dto.getAssigneeId());
        assertEquals("MEDIUM", dto.getPriority());
        assertEquals(labels, dto.getLabels());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        TaskDto dto = new TaskDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setTitle("Updated task");
        dto.setPriority("LOW");
        dto.setLabels(List.of("refactor"));

        assertEquals(id, dto.getId());
        assertEquals("Updated task", dto.getTitle());
        assertEquals("LOW", dto.getPriority());
        assertEquals(List.of("refactor"), dto.getLabels());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskDto dto1 = TaskDto.builder()
                .id(id)
                .tenantId(tenantId)
                .title("Task A")
                .build();

        TaskDto dto2 = TaskDto.builder()
                .id(id)
                .tenantId(tenantId)
                .title("Task A")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TaskDto dto1 = TaskDto.builder()
                .title("Task A")
                .build();

        TaskDto dto2 = TaskDto.builder()
                .title("Task B")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        TaskDto dto = TaskDto.builder()
                .title("Fix login")
                .externalId("JIRA-999")
                .priority("CRITICAL")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("Fix login"));
        assertTrue(str.contains("JIRA-999"));
        assertTrue(str.contains("CRITICAL"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        TaskDto dto = TaskDto.builder()
                .id(null)
                .tenantId(null)
                .title(null)
                .description(null)
                .labels(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTitle());
        assertNull(dto.getDescription());
        assertNull(dto.getLabels());
    }

    @Test
    void should_handleEmptyLabels_when_emptyListProvided() {
        TaskDto dto = TaskDto.builder()
                .labels(Collections.emptyList())
                .build();

        assertNotNull(dto.getLabels());
        assertTrue(dto.getLabels().isEmpty());
    }
}
