package com.squadron.orchestrator.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskWorkflowTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID transitionedBy = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .previousState(null)
                .transitionAt(now)
                .transitionedBy(transitionedBy)
                .metadata("{}")
                .build();

        assertEquals(id, workflow.getId());
        assertEquals(tenantId, workflow.getTenantId());
        assertEquals(taskId, workflow.getTaskId());
        assertEquals("BACKLOG", workflow.getCurrentState());
        assertNull(workflow.getPreviousState());
        assertEquals(now, workflow.getTransitionAt());
        assertEquals(transitionedBy, workflow.getTransitionedBy());
        assertEquals("{}", workflow.getMetadata());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskWorkflow workflow = new TaskWorkflow();
        assertNull(workflow.getId());
        assertNull(workflow.getCurrentState());
    }

    @Test
    void should_setAndGetFields() {
        TaskWorkflow workflow = new TaskWorkflow();
        workflow.setCurrentState("PLANNING");
        workflow.setPreviousState("BACKLOG");

        assertEquals("PLANNING", workflow.getCurrentState());
        assertEquals("BACKLOG", workflow.getPreviousState());
    }

    @Test
    void should_implementEquals() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflow w1 = TaskWorkflow.builder()
                .id(id).tenantId(tenantId).taskId(taskId).currentState("BACKLOG").transitionAt(now).build();
        TaskWorkflow w2 = TaskWorkflow.builder()
                .id(id).tenantId(tenantId).taskId(taskId).currentState("BACKLOG").transitionAt(now).build();

        assertEquals(w1, w2);
        assertEquals(w1.hashCode(), w2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        TaskWorkflow w1 = TaskWorkflow.builder().id(UUID.randomUUID()).currentState("BACKLOG").build();
        TaskWorkflow w2 = TaskWorkflow.builder().id(UUID.randomUUID()).currentState("BACKLOG").build();

        assertNotEquals(w1, w2);
    }

    @Test
    void should_haveToString() {
        TaskWorkflow workflow = TaskWorkflow.builder().currentState("QA").build();
        assertNotNull(workflow.toString());
        assert workflow.toString().contains("QA");
    }
}
