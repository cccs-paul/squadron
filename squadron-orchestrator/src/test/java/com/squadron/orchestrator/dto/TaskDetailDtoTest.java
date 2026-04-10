package com.squadron.orchestrator.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDetailDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_createWithBuilder_when_allFieldsProvided() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        List<String> labels = List.of("bug", "priority-high");
        List<String> transitions = List.of("PLANNING", "REVIEW", "DONE");

        TaskDetailDto dto = TaskDetailDto.builder()
                .id(id)
                .tenantId(tenantId)
                .projectId(projectId)
                .teamId(teamId)
                .assigneeId(assigneeId)
                .title("Fix login bug")
                .description("Users cannot log in with SSO")
                .externalId("JIRA-123")
                .externalUrl("https://jira.example.com/browse/JIRA-123")
                .priority("HIGH")
                .labels(labels)
                .tokenUsage(15000)
                .currentState("IN_PROGRESS")
                .previousState("PLANNING")
                .lastTransitionAt("2026-04-08T14:30:00Z")
                .availableTransitions(transitions)
                .projectName("Auth Service")
                .mappedExternalStatus("In Progress")
                .createdAt("2026-04-01T00:00:00Z")
                .updatedAt("2026-04-09T10:00:00Z")
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(assigneeId, dto.getAssigneeId());
        assertEquals("Fix login bug", dto.getTitle());
        assertEquals("Users cannot log in with SSO", dto.getDescription());
        assertEquals("JIRA-123", dto.getExternalId());
        assertEquals("https://jira.example.com/browse/JIRA-123", dto.getExternalUrl());
        assertEquals("HIGH", dto.getPriority());
        assertEquals(labels, dto.getLabels());
        assertEquals(15000L, dto.getTokenUsage());
        assertEquals("IN_PROGRESS", dto.getCurrentState());
        assertEquals("PLANNING", dto.getPreviousState());
        assertEquals("2026-04-08T14:30:00Z", dto.getLastTransitionAt());
        assertEquals(transitions, dto.getAvailableTransitions());
        assertEquals("Auth Service", dto.getProjectName());
        assertEquals("In Progress", dto.getMappedExternalStatus());
        assertEquals("2026-04-01T00:00:00Z", dto.getCreatedAt());
        assertEquals("2026-04-09T10:00:00Z", dto.getUpdatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor_when_defaultsExpected() {
        TaskDetailDto dto = new TaskDetailDto();

        assertNull(dto.getId());
        assertNull(dto.getTitle());
        assertNull(dto.getLabels());
        assertNull(dto.getAvailableTransitions());
        assertNull(dto.getCurrentState());
        assertEquals(0L, dto.getTokenUsage());
    }

    @Test
    void should_createWithAllArgsConstructor_when_allParametersPassed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        List<String> labels = List.of("feature");
        List<String> transitions = List.of("DONE");

        TaskDetailDto dto = new TaskDetailDto(
                id, tenantId, projectId, teamId, assigneeId,
                "Title", "Description", "EXT-1", "https://ext.url",
                "MEDIUM", labels, 5000L,
                "REVIEW", "IN_PROGRESS", "2026-04-09T00:00:00Z", transitions,
                "Project X", "Under Review",
                "2026-03-01T00:00:00Z", "2026-04-09T00:00:00Z"
        );

        assertEquals(id, dto.getId());
        assertEquals("Title", dto.getTitle());
        assertEquals("REVIEW", dto.getCurrentState());
        assertEquals(labels, dto.getLabels());
        assertEquals(transitions, dto.getAvailableTransitions());
        assertEquals(5000L, dto.getTokenUsage());
        assertEquals("Project X", dto.getProjectName());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        TaskDetailDto dto = new TaskDetailDto();
        UUID id = UUID.randomUUID();
        List<String> labels = new ArrayList<>();
        labels.add("refactor");

        dto.setId(id);
        dto.setTitle("Refactor service");
        dto.setCurrentState("PLANNING");
        dto.setPreviousState("BACKLOG");
        dto.setLabels(labels);
        dto.setTokenUsage(8500);
        dto.setProjectName("Core API");
        dto.setMappedExternalStatus("To Do");

        assertEquals(id, dto.getId());
        assertEquals("Refactor service", dto.getTitle());
        assertEquals("PLANNING", dto.getCurrentState());
        assertEquals("BACKLOG", dto.getPreviousState());
        assertEquals(labels, dto.getLabels());
        assertEquals(8500L, dto.getTokenUsage());
        assertEquals("Core API", dto.getProjectName());
        assertEquals("To Do", dto.getMappedExternalStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<String> labels = List.of("bug");

        TaskDetailDto dto1 = TaskDetailDto.builder()
                .id(id).tenantId(tenantId).title("Bug").labels(labels).tokenUsage(100).build();
        TaskDetailDto dto2 = TaskDetailDto.builder()
                .id(id).tenantId(tenantId).title("Bug").labels(labels).tokenUsage(100).build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TaskDetailDto dto1 = TaskDetailDto.builder().title("Task A").tokenUsage(100).build();
        TaskDetailDto dto2 = TaskDetailDto.builder().title("Task B").tokenUsage(200).build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_haveToString_when_called() {
        TaskDetailDto dto = TaskDetailDto.builder()
                .title("ToString Task")
                .currentState("REVIEW")
                .build();

        assertNotNull(dto.toString());
        assertTrue(dto.toString().contains("ToString Task"));
        assertTrue(dto.toString().contains("REVIEW"));
    }

    @Test
    void should_handleEmptyLabels_when_emptyListProvided() {
        TaskDetailDto dto = TaskDetailDto.builder()
                .title("No Labels")
                .labels(List.of())
                .availableTransitions(List.of())
                .build();

        assertNotNull(dto.getLabels());
        assertTrue(dto.getLabels().isEmpty());
        assertNotNull(dto.getAvailableTransitions());
        assertTrue(dto.getAvailableTransitions().isEmpty());
    }

    @Test
    void should_roundTripJson_when_serializeAndDeserialize() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        List<String> labels = List.of("enhancement", "backend");
        List<String> transitions = List.of("REVIEW", "DONE");

        TaskDetailDto original = TaskDetailDto.builder()
                .id(id)
                .tenantId(tenantId)
                .projectId(projectId)
                .title("JSON Roundtrip Task")
                .description("Testing Jackson serialization")
                .externalId("GH-456")
                .priority("LOW")
                .labels(labels)
                .tokenUsage(25000)
                .currentState("IN_PROGRESS")
                .previousState("PLANNING")
                .lastTransitionAt("2026-04-08T09:00:00Z")
                .availableTransitions(transitions)
                .projectName("Squadron Core")
                .mappedExternalStatus("Active")
                .createdAt("2026-03-15T00:00:00Z")
                .updatedAt("2026-04-09T08:00:00Z")
                .build();

        String json = mapper.writeValueAsString(original);
        TaskDetailDto deserialized = mapper.readValue(json, TaskDetailDto.class);

        assertEquals(original, deserialized);
    }

    @Test
    void should_serializeToJson_when_usingJackson() throws Exception {
        UUID id = UUID.randomUUID();

        TaskDetailDto dto = TaskDetailDto.builder()
                .id(id)
                .title("Serialize Test")
                .currentState("DONE")
                .tokenUsage(9999)
                .labels(List.of("urgent"))
                .build();

        String json = mapper.writeValueAsString(dto);

        assertNotNull(json);
        assertTrue(json.contains("\"title\":\"Serialize Test\""));
        assertTrue(json.contains("\"currentState\":\"DONE\""));
        assertTrue(json.contains("\"tokenUsage\":9999"));
        assertTrue(json.contains("\"urgent\""));
        assertTrue(json.contains(id.toString()));
    }

    @Test
    void should_deserializeFromJson_when_validJsonProvided() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
                {
                    "id": "%s",
                    "title": "From JSON",
                    "priority": "CRITICAL",
                    "labels": ["security", "p0"],
                    "tokenUsage": 50000,
                    "currentState": "IN_PROGRESS",
                    "availableTransitions": ["REVIEW", "BLOCKED"],
                    "projectName": "Infra"
                }
                """.formatted(id);

        TaskDetailDto dto = mapper.readValue(json, TaskDetailDto.class);

        assertEquals(id, dto.getId());
        assertEquals("From JSON", dto.getTitle());
        assertEquals("CRITICAL", dto.getPriority());
        assertEquals(List.of("security", "p0"), dto.getLabels());
        assertEquals(50000L, dto.getTokenUsage());
        assertEquals("IN_PROGRESS", dto.getCurrentState());
        assertEquals(List.of("REVIEW", "BLOCKED"), dto.getAvailableTransitions());
        assertEquals("Infra", dto.getProjectName());
    }
}
