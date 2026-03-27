package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void should_buildMessage_when_createdWithStringId() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "abc-123");

        assertEquals("Resource User not found: abc-123", ex.getMessage());
    }

    @Test
    void should_buildMessage_when_createdWithUuidId() {
        UUID id = UUID.randomUUID();
        ResourceNotFoundException ex = new ResourceNotFoundException("Task", id);

        assertEquals("Resource Task not found: " + id, ex.getMessage());
    }

    @Test
    void should_setErrorCodeToNotFound_when_created() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Tenant", "t-1");

        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void should_beSquadronException_when_created() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Team", "team-1");

        assertInstanceOf(SquadronException.class, ex);
    }

    @Test
    void should_includeResourceTypeInMessage_when_created() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Workspace", "ws-1");

        assertTrue(ex.getMessage().contains("Workspace"));
        assertTrue(ex.getMessage().contains("ws-1"));
    }

    @Test
    void should_buildMessage_when_createdWithNumericId() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Item", 42);

        assertEquals("Resource Item not found: 42", ex.getMessage());
    }
}
