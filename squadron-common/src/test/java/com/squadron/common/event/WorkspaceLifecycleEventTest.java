package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLifecycleEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();

        assertEquals("WORKSPACE_LIFECYCLE", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent(workspaceId, taskId, "CREATE");

        assertEquals("WORKSPACE_LIFECYCLE", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent(workspaceId, taskId, "DESTROY");

        assertEquals(workspaceId, event.getWorkspaceId());
        assertEquals(taskId, event.getTaskId());
        assertEquals("DESTROY", event.getAction());
    }

    @Test
    void should_haveNullFields_when_defaultConstructorUsed() {
        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();

        assertNull(event.getWorkspaceId());
        assertNull(event.getTaskId());
        assertNull(event.getAction());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        event.setWorkspaceId(workspaceId);
        event.setTaskId(taskId);
        event.setAction("SUSPEND");

        assertEquals(workspaceId, event.getWorkspaceId());
        assertEquals(taskId, event.getTaskId());
        assertEquals("SUSPEND", event.getAction());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
