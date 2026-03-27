package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnauthorizedExceptionTest {

    @Test
    void should_setMessage_when_created() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");

        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void should_setErrorCodeToUnauthorized_when_created() {
        UnauthorizedException ex = new UnauthorizedException("Token expired");

        assertEquals("UNAUTHORIZED", ex.getErrorCode());
    }

    @Test
    void should_beSquadronException_when_created() {
        UnauthorizedException ex = new UnauthorizedException("test");

        assertInstanceOf(SquadronException.class, ex);
    }

    @Test
    void should_beRuntimeException_when_created() {
        UnauthorizedException ex = new UnauthorizedException("test");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void should_preserveMessage_when_differentMessagesUsed() {
        UnauthorizedException ex1 = new UnauthorizedException("Bad token");
        UnauthorizedException ex2 = new UnauthorizedException("Missing header");

        assertEquals("Bad token", ex1.getMessage());
        assertEquals("Missing header", ex2.getMessage());
        assertEquals("UNAUTHORIZED", ex1.getErrorCode());
        assertEquals("UNAUTHORIZED", ex2.getErrorCode());
    }
}
