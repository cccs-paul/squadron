package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskWorkflowDtoTest {

    @Test
    void should_createWithBuilder() {
        UUID taskId = UUID.randomUUID();
        UUID transitionedBy = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflowDto dto = TaskWorkflowDto.builder()
                .taskId(taskId)
                .currentState("PLANNING")
                .previousState("PRIORITIZED")
                .transitionAt(now)
                .transitionedBy(transitionedBy)
                .metadata("{}")
                .build();

        assertEquals(taskId, dto.getTaskId());
        assertEquals("PLANNING", dto.getCurrentState());
        assertEquals("PRIORITIZED", dto.getPreviousState());
        assertEquals(now, dto.getTransitionAt());
        assertEquals(transitionedBy, dto.getTransitionedBy());
        assertEquals("{}", dto.getMetadata());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskWorkflowDto dto = new TaskWorkflowDto();
        assertNull(dto.getTaskId());
        assertNull(dto.getCurrentState());
    }

    @Test
    void should_setAndGetFields() {
        TaskWorkflowDto dto = new TaskWorkflowDto();
        UUID taskId = UUID.randomUUID();
        dto.setTaskId(taskId);
        dto.setCurrentState("QA");

        assertEquals(taskId, dto.getTaskId());
        assertEquals("QA", dto.getCurrentState());
    }

    @Test
    void should_implementEquals() {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflowDto d1 = TaskWorkflowDto.builder()
                .taskId(taskId).currentState("QA").transitionAt(now).build();
        TaskWorkflowDto d2 = TaskWorkflowDto.builder()
                .taskId(taskId).currentState("QA").transitionAt(now).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        TaskWorkflowDto d1 = TaskWorkflowDto.builder().currentState("QA").build();
        TaskWorkflowDto d2 = TaskWorkflowDto.builder().currentState("DONE").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString() {
        TaskWorkflowDto dto = TaskWorkflowDto.builder().currentState("MERGE").build();
        assertNotNull(dto.toString());
        assert dto.toString().contains("MERGE");
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID taskId = UUID.randomUUID();
        UUID transitionedBy = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflowDto dto = new TaskWorkflowDto(taskId, "REVIEW", "PROPOSE_CODE", now, transitionedBy, "{}");

        assertEquals(taskId, dto.getTaskId());
        assertEquals("REVIEW", dto.getCurrentState());
        assertEquals("PROPOSE_CODE", dto.getPreviousState());
    }
}
