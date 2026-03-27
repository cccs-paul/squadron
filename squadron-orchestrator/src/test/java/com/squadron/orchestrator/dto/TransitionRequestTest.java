package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransitionRequestTest {

    @Test
    void should_createWithBuilder() {
        UUID taskId = UUID.randomUUID();

        TransitionRequest request = TransitionRequest.builder()
                .taskId(taskId)
                .targetState("PLANNING")
                .reason("Ready for planning")
                .build();

        assertEquals(taskId, request.getTaskId());
        assertEquals("PLANNING", request.getTargetState());
        assertEquals("Ready for planning", request.getReason());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TransitionRequest request = new TransitionRequest();
        assertNull(request.getTaskId());
        assertNull(request.getTargetState());
        assertNull(request.getReason());
    }

    @Test
    void should_setAndGetFields() {
        TransitionRequest request = new TransitionRequest();
        UUID taskId = UUID.randomUUID();
        request.setTaskId(taskId);
        request.setTargetState("REVIEW");
        request.setReason("Code complete");

        assertEquals(taskId, request.getTaskId());
        assertEquals("REVIEW", request.getTargetState());
        assertEquals("Code complete", request.getReason());
    }

    @Test
    void should_implementEquals() {
        UUID taskId = UUID.randomUUID();

        TransitionRequest r1 = TransitionRequest.builder()
                .taskId(taskId).targetState("PLANNING").reason("r").build();
        TransitionRequest r2 = TransitionRequest.builder()
                .taskId(taskId).targetState("PLANNING").reason("r").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        UUID taskId = UUID.randomUUID();
        TransitionRequest r1 = TransitionRequest.builder().taskId(taskId).targetState("A").build();
        TransitionRequest r2 = TransitionRequest.builder().taskId(taskId).targetState("B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        TransitionRequest request = TransitionRequest.builder().targetState("QA").build();
        assertNotNull(request.toString());
        assert request.toString().contains("QA");
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID taskId = UUID.randomUUID();
        TransitionRequest request = new TransitionRequest(taskId, "DONE", "Finished");

        assertEquals(taskId, request.getTaskId());
        assertEquals("DONE", request.getTargetState());
        assertEquals("Finished", request.getReason());
    }
}
