package com.squadron.orchestrator.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        Task task = Task.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("JIRA-1")
                .externalUrl("https://jira.example.com/JIRA-1")
                .title("Fix bug")
                .description("Description")
                .assigneeId(assigneeId)
                .priority("HIGH")
                .labels("[\"bug\"]")
                .build();

        assertEquals(id, task.getId());
        assertEquals(tenantId, task.getTenantId());
        assertEquals(teamId, task.getTeamId());
        assertEquals(projectId, task.getProjectId());
        assertEquals("JIRA-1", task.getExternalId());
        assertEquals("https://jira.example.com/JIRA-1", task.getExternalUrl());
        assertEquals("Fix bug", task.getTitle());
        assertEquals("Description", task.getDescription());
        assertEquals(assigneeId, task.getAssigneeId());
        assertEquals("HIGH", task.getPriority());
        assertEquals("[\"bug\"]", task.getLabels());
    }

    @Test
    void should_setTimestampsOnPrePersist() {
        Task task = new Task();
        task.onCreate();

        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        assertEquals(task.getCreatedAt(), task.getUpdatedAt());
    }

    @Test
    void should_updateTimestampOnPreUpdate() {
        Task task = new Task();
        task.onCreate();

        task.onUpdate();

        assertNotNull(task.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields() {
        Task task = new Task();
        task.setTitle("New Title");
        task.setPriority("LOW");

        assertEquals("New Title", task.getTitle());
        assertEquals("LOW", task.getPriority());
    }

    @Test
    void should_implementEquals() {
        UUID id = UUID.randomUUID();
        Task t1 = Task.builder().id(id).title("T").build();
        Task t2 = Task.builder().id(id).title("T").build();

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        Task t1 = Task.builder().id(UUID.randomUUID()).title("T").build();
        Task t2 = Task.builder().id(UUID.randomUUID()).title("T").build();

        assertNotEquals(t1, t2);
    }

    @Test
    void should_haveToString() {
        Task task = Task.builder().title("My Task").build();
        assertNotNull(task.toString());
        assert task.toString().contains("My Task");
    }

    @Test
    void should_createWithNoArgsConstructor() {
        Task task = new Task();
        assertNull(task.getId());
        assertNull(task.getTitle());
    }
}
