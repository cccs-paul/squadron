package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateTaskRequestTest {

    @Test
    void should_createWithBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("JIRA-123")
                .externalUrl("https://jira.example.com/JIRA-123")
                .title("Fix bug")
                .description("Fix the login bug")
                .assigneeId(assigneeId)
                .priority("HIGH")
                .labels("[\"bug\",\"urgent\"]")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("JIRA-123", request.getExternalId());
        assertEquals("https://jira.example.com/JIRA-123", request.getExternalUrl());
        assertEquals("Fix bug", request.getTitle());
        assertEquals("Fix the login bug", request.getDescription());
        assertEquals(assigneeId, request.getAssigneeId());
        assertEquals("HIGH", request.getPriority());
        assertEquals("[\"bug\",\"urgent\"]", request.getLabels());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreateTaskRequest request = new CreateTaskRequest();
        assertNull(request.getTenantId());
        assertNull(request.getTitle());
    }

    @Test
    void should_setAndGetFields() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("New Title");
        request.setPriority("LOW");

        assertEquals("New Title", request.getTitle());
        assertEquals("LOW", request.getPriority());
    }

    @Test
    void should_implementEquals() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CreateTaskRequest r1 = CreateTaskRequest.builder()
                .tenantId(tenantId).teamId(teamId).projectId(projectId).title("T").build();
        CreateTaskRequest r2 = CreateTaskRequest.builder()
                .tenantId(tenantId).teamId(teamId).projectId(projectId).title("T").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        CreateTaskRequest r1 = CreateTaskRequest.builder().title("A").build();
        CreateTaskRequest r2 = CreateTaskRequest.builder().title("B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        CreateTaskRequest request = CreateTaskRequest.builder().title("Test").build();
        assertNotNull(request.toString());
        assert request.toString().contains("Test");
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        CreateTaskRequest request = new CreateTaskRequest(
                tenantId, teamId, projectId, "ext-1", "url", "Title",
                "Desc", assigneeId, "HIGH", "[\"label\"]"
        );

        assertEquals(tenantId, request.getTenantId());
        assertEquals("Title", request.getTitle());
        assertEquals(assigneeId, request.getAssigneeId());
    }
}
