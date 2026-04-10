package com.squadron.orchestrator.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSummaryDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_createWithBuilder_when_allFieldsProvided() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID gitConnectionId = UUID.randomUUID();
        Map<String, Long> taskCounts = Map.of("OPEN", 5L, "DONE", 10L);

        ProjectSummaryDto dto = ProjectSummaryDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("My Project")
                .description("A test project")
                .repositoryUrl("https://github.com/org/repo")
                .defaultBranch("main")
                .branchNamingTemplate("{type}/{ticket}-{description}")
                .connectionId(connectionId)
                .externalProjectId("EXT-42")
                .gitConnectionId(gitConnectionId)
                .totalTasks(15)
                .activeTasks(5)
                .taskCountsByState(taskCounts)
                .workflowMappingsConfigured(true)
                .workflowMappingCount(3)
                .createdAt("2026-01-01T00:00:00Z")
                .updatedAt("2026-04-09T12:00:00Z")
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals("My Project", dto.getName());
        assertEquals("A test project", dto.getDescription());
        assertEquals("https://github.com/org/repo", dto.getRepositoryUrl());
        assertEquals("main", dto.getDefaultBranch());
        assertEquals("{type}/{ticket}-{description}", dto.getBranchNamingTemplate());
        assertEquals(connectionId, dto.getConnectionId());
        assertEquals("EXT-42", dto.getExternalProjectId());
        assertEquals(gitConnectionId, dto.getGitConnectionId());
        assertEquals(15L, dto.getTotalTasks());
        assertEquals(5L, dto.getActiveTasks());
        assertEquals(taskCounts, dto.getTaskCountsByState());
        assertTrue(dto.isWorkflowMappingsConfigured());
        assertEquals(3, dto.getWorkflowMappingCount());
        assertEquals("2026-01-01T00:00:00Z", dto.getCreatedAt());
        assertEquals("2026-04-09T12:00:00Z", dto.getUpdatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor_when_defaultsExpected() {
        ProjectSummaryDto dto = new ProjectSummaryDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getName());
        assertNull(dto.getTaskCountsByState());
        assertEquals(0L, dto.getTotalTasks());
        assertEquals(0L, dto.getActiveTasks());
        assertFalse(dto.isWorkflowMappingsConfigured());
        assertEquals(0, dto.getWorkflowMappingCount());
    }

    @Test
    void should_createWithAllArgsConstructor_when_allParametersPassed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID gitConnectionId = UUID.randomUUID();
        Map<String, Long> taskCounts = Map.of("PLANNING", 2L);

        ProjectSummaryDto dto = new ProjectSummaryDto(
                id, tenantId, teamId, "Name", "Desc", "https://repo.url",
                "develop", "feature/{ticket}", connectionId, "PRJ-1",
                gitConnectionId, 100L, 25L, taskCounts, true, 7,
                "2026-01-01T00:00:00Z", "2026-04-09T00:00:00Z"
        );

        assertEquals(id, dto.getId());
        assertEquals("Name", dto.getName());
        assertEquals(100L, dto.getTotalTasks());
        assertEquals(25L, dto.getActiveTasks());
        assertTrue(dto.isWorkflowMappingsConfigured());
        assertEquals(7, dto.getWorkflowMappingCount());
        assertEquals(gitConnectionId, dto.getGitConnectionId());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ProjectSummaryDto dto = new ProjectSummaryDto();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Long> taskCounts = new HashMap<>();
        taskCounts.put("REVIEW", 3L);

        dto.setId(id);
        dto.setTenantId(tenantId);
        dto.setName("Setter Project");
        dto.setDescription("Set via setters");
        dto.setRepositoryUrl("https://github.com/test");
        dto.setDefaultBranch("main");
        dto.setTotalTasks(50);
        dto.setActiveTasks(10);
        dto.setTaskCountsByState(taskCounts);
        dto.setWorkflowMappingsConfigured(true);
        dto.setWorkflowMappingCount(2);
        dto.setCreatedAt("2026-03-01T00:00:00Z");
        dto.setUpdatedAt("2026-04-01T00:00:00Z");

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("Setter Project", dto.getName());
        assertEquals("Set via setters", dto.getDescription());
        assertEquals(50L, dto.getTotalTasks());
        assertEquals(10L, dto.getActiveTasks());
        assertEquals(taskCounts, dto.getTaskCountsByState());
        assertTrue(dto.isWorkflowMappingsConfigured());
        assertEquals(2, dto.getWorkflowMappingCount());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ProjectSummaryDto dto1 = ProjectSummaryDto.builder()
                .id(id).tenantId(tenantId).name("Test").totalTasks(5).build();
        ProjectSummaryDto dto2 = ProjectSummaryDto.builder()
                .id(id).tenantId(tenantId).name("Test").totalTasks(5).build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ProjectSummaryDto dto1 = ProjectSummaryDto.builder().name("Alpha").totalTasks(1).build();
        ProjectSummaryDto dto2 = ProjectSummaryDto.builder().name("Beta").totalTasks(2).build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_haveToString_when_called() {
        ProjectSummaryDto dto = ProjectSummaryDto.builder().name("ToString Project").build();

        assertNotNull(dto.toString());
        assertTrue(dto.toString().contains("ToString Project"));
    }

    @Test
    void should_serializeToJson_when_usingJackson() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Long> taskCounts = Map.of("OPEN", 3L, "DONE", 7L);

        ProjectSummaryDto dto = ProjectSummaryDto.builder()
                .id(id)
                .name("JSON Project")
                .totalTasks(10)
                .activeTasks(3)
                .taskCountsByState(taskCounts)
                .workflowMappingsConfigured(true)
                .workflowMappingCount(2)
                .build();

        String json = mapper.writeValueAsString(dto);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"JSON Project\""));
        assertTrue(json.contains("\"totalTasks\":10"));
        assertTrue(json.contains("\"workflowMappingsConfigured\":true"));
        assertTrue(json.contains(id.toString()));
    }

    @Test
    void should_deserializeFromJson_when_validJsonProvided() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                    "id": "%s",
                    "name": "Deserialized",
                    "totalTasks": 42,
                    "activeTasks": 8,
                    "taskCountsByState": {"PLANNING": 5, "REVIEW": 3},
                    "workflowMappingsConfigured": false,
                    "workflowMappingCount": 0,
                    "createdAt": "2026-01-15T10:00:00Z"
                }
                """.formatted(id);

        ProjectSummaryDto dto = mapper.readValue(json, ProjectSummaryDto.class);

        assertEquals(id, dto.getId());
        assertEquals("Deserialized", dto.getName());
        assertEquals(42L, dto.getTotalTasks());
        assertEquals(8L, dto.getActiveTasks());
        assertEquals(5L, dto.getTaskCountsByState().get("PLANNING"));
        assertEquals(3L, dto.getTaskCountsByState().get("REVIEW"));
        assertFalse(dto.isWorkflowMappingsConfigured());
        assertEquals("2026-01-15T10:00:00Z", dto.getCreatedAt());
    }

    @Test
    void should_roundTripJson_when_serializeAndDeserialize() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Long> taskCounts = Map.of("OPEN", 1L);

        ProjectSummaryDto original = ProjectSummaryDto.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Roundtrip")
                .description("Testing JSON roundtrip")
                .repositoryUrl("https://github.com/org/roundtrip")
                .defaultBranch("main")
                .totalTasks(10)
                .activeTasks(4)
                .taskCountsByState(taskCounts)
                .workflowMappingsConfigured(true)
                .workflowMappingCount(5)
                .createdAt("2026-02-01T00:00:00Z")
                .updatedAt("2026-04-01T00:00:00Z")
                .build();

        String json = mapper.writeValueAsString(original);
        ProjectSummaryDto deserialized = mapper.readValue(json, ProjectSummaryDto.class);

        assertEquals(original, deserialized);
    }

    @Test
    void should_handleNullTaskCountsByState_when_mapNotSet() throws Exception {
        ProjectSummaryDto dto = ProjectSummaryDto.builder()
                .name("No Counts")
                .build();

        String json = mapper.writeValueAsString(dto);
        ProjectSummaryDto deserialized = mapper.readValue(json, ProjectSummaryDto.class);

        assertNull(deserialized.getTaskCountsByState());
        assertEquals("No Counts", deserialized.getName());
    }
}
