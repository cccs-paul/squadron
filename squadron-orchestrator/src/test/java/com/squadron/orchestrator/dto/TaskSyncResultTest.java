package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskSyncResultTest {

    @Test
    void should_createWithBuilder() {
        List<String> errors = List.of("Failed to sync JIRA-100", "Timeout on JIRA-200");

        TaskSyncResult result = TaskSyncResult.builder()
                .created(5)
                .updated(3)
                .unchanged(10)
                .failed(2)
                .errors(errors)
                .build();

        assertEquals(5, result.getCreated());
        assertEquals(3, result.getUpdated());
        assertEquals(10, result.getUnchanged());
        assertEquals(2, result.getFailed());
        assertEquals(errors, result.getErrors());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskSyncResult result = new TaskSyncResult();
        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertNull(result.getErrors());
    }

    @Test
    void should_setAndGetFields() {
        TaskSyncResult result = new TaskSyncResult();
        result.setCreated(10);
        result.setUpdated(5);
        result.setUnchanged(20);
        result.setFailed(1);
        result.setErrors(List.of("error1"));

        assertEquals(10, result.getCreated());
        assertEquals(5, result.getUpdated());
        assertEquals(20, result.getUnchanged());
        assertEquals(1, result.getFailed());
        assertEquals(List.of("error1"), result.getErrors());
    }

    @Test
    void should_implementEquals() {
        List<String> errors = List.of("err");

        TaskSyncResult r1 = TaskSyncResult.builder()
                .created(1).updated(2).unchanged(3).failed(0).errors(errors).build();
        TaskSyncResult r2 = TaskSyncResult.builder()
                .created(1).updated(2).unchanged(3).failed(0).errors(errors).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        TaskSyncResult r1 = TaskSyncResult.builder().created(1).build();
        TaskSyncResult r2 = TaskSyncResult.builder().created(2).build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        TaskSyncResult result = TaskSyncResult.builder().created(7).updated(3).build();
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("7"));
        assertTrue(str.contains("3"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        List<String> errors = List.of("e1", "e2");
        TaskSyncResult result = new TaskSyncResult(4, 2, 8, 1, errors);

        assertEquals(4, result.getCreated());
        assertEquals(2, result.getUpdated());
        assertEquals(8, result.getUnchanged());
        assertEquals(1, result.getFailed());
        assertEquals(errors, result.getErrors());
    }

    @Test
    void should_handleEmptyErrorsList() {
        TaskSyncResult result = TaskSyncResult.builder()
                .created(0).updated(0).unchanged(0).failed(0)
                .errors(Collections.emptyList())
                .build();

        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void should_handleNullErrors() {
        TaskSyncResult result = TaskSyncResult.builder()
                .errors(null)
                .build();

        assertNull(result.getErrors());
    }
}
