package com.squadron.orchestrator.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskStateHistoryTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID taskWorkflowId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateHistory history = TaskStateHistory.builder()
                .id(id)
                .taskWorkflowId(taskWorkflowId)
                .fromState("BACKLOG")
                .toState("PRIORITIZED")
                .triggeredBy(triggeredBy)
                .reason("Prioritized for sprint")
                .build();

        assertEquals(id, history.getId());
        assertEquals(taskWorkflowId, history.getTaskWorkflowId());
        assertEquals("BACKLOG", history.getFromState());
        assertEquals("PRIORITIZED", history.getToState());
        assertEquals(triggeredBy, history.getTriggeredBy());
        assertEquals("Prioritized for sprint", history.getReason());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskStateHistory history = new TaskStateHistory();
        assertNull(history.getId());
        assertNull(history.getFromState());
    }

    @Test
    void should_setTimestampOnPrePersist() {
        TaskStateHistory history = new TaskStateHistory();
        history.onCreate();

        assertNotNull(history.getCreatedAt());
    }

    @Test
    void should_allowNullFromState() {
        TaskStateHistory history = TaskStateHistory.builder()
                .taskWorkflowId(UUID.randomUUID())
                .fromState(null)
                .toState("BACKLOG")
                .triggeredBy(UUID.randomUUID())
                .reason("Workflow initialized")
                .build();

        assertNull(history.getFromState());
        assertEquals("BACKLOG", history.getToState());
    }

    @Test
    void should_setAndGetFields() {
        TaskStateHistory history = new TaskStateHistory();
        history.setFromState("REVIEW");
        history.setToState("QA");
        history.setReason("Review passed");

        assertEquals("REVIEW", history.getFromState());
        assertEquals("QA", history.getToState());
        assertEquals("Review passed", history.getReason());
    }

    @Test
    void should_haveToString() {
        TaskStateHistory history = TaskStateHistory.builder().toState("DONE").build();
        assertNotNull(history.toString());
        assert history.toString().contains("DONE");
    }
}
