package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskStateChangedEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();

        assertEquals("TASK_STATE_CHANGED", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent(taskId, "OPEN", "IN_PROGRESS", triggeredBy, "Started work");

        assertEquals("TASK_STATE_CHANGED", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent(taskId, "OPEN", "IN_PROGRESS", triggeredBy, "Started work");

        assertEquals(taskId, event.getTaskId());
        assertEquals("OPEN", event.getFromState());
        assertEquals("IN_PROGRESS", event.getToState());
        assertEquals(triggeredBy, event.getTriggeredBy());
        assertEquals("Started work", event.getReason());
    }

    @Test
    void should_haveNullFields_when_defaultConstructorUsed() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();

        assertNull(event.getTaskId());
        assertNull(event.getFromState());
        assertNull(event.getToState());
        assertNull(event.getTriggeredBy());
        assertNull(event.getReason());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        UUID taskId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        event.setTaskId(taskId);
        event.setFromState("DRAFT");
        event.setToState("OPEN");
        event.setTriggeredBy(triggeredBy);
        event.setReason("Approved");

        assertEquals(taskId, event.getTaskId());
        assertEquals("DRAFT", event.getFromState());
        assertEquals("OPEN", event.getToState());
        assertEquals(triggeredBy, event.getTriggeredBy());
        assertEquals("Approved", event.getReason());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
