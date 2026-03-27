package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForbiddenExceptionTest {

    @Test
    void should_setMessage_when_created() {
        ForbiddenException ex = new ForbiddenException("Access denied");

        assertEquals("Access denied", ex.getMessage());
    }

    @Test
    void should_setErrorCodeToForbidden_when_created() {
        ForbiddenException ex = new ForbiddenException("No permission");

        assertEquals("FORBIDDEN", ex.getErrorCode());
    }

    @Test
    void should_beSquadronException_when_created() {
        ForbiddenException ex = new ForbiddenException("test");

        assertInstanceOf(SquadronException.class, ex);
    }

    @Test
    void should_beRuntimeException_when_created() {
        ForbiddenException ex = new ForbiddenException("test");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void should_preserveMessage_when_differentMessagesUsed() {
        ForbiddenException ex1 = new ForbiddenException("Cannot read");
        ForbiddenException ex2 = new ForbiddenException("Cannot write");

        assertEquals("Cannot read", ex1.getMessage());
        assertEquals("Cannot write", ex2.getMessage());
        assertEquals("FORBIDDEN", ex1.getErrorCode());
        assertEquals("FORBIDDEN", ex2.getErrorCode());
    }
}
