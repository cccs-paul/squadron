package com.squadron.identity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationExceptionTest {

    @Test
    void should_createException_when_messageProvided() {
        AuthenticationException ex = new AuthenticationException("Invalid token");

        assertEquals("Invalid token", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void should_createException_when_messageAndCauseProvided() {
        Throwable cause = new RuntimeException("original error");
        AuthenticationException ex = new AuthenticationException("Auth failed", cause);

        assertEquals("Auth failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void should_beRuntimeException_when_checked() {
        AuthenticationException ex = new AuthenticationException("test");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void should_haveUnauthorizedResponseStatus_when_checked() {
        ResponseStatus annotation = AuthenticationException.class.getAnnotation(ResponseStatus.class);

        assertNotNull(annotation);
        assertEquals(HttpStatus.UNAUTHORIZED, annotation.value());
    }

    @Test
    void should_preserveCauseChain_when_nestedExceptions() {
        Exception root = new IllegalStateException("root cause");
        Exception middle = new RuntimeException("middle", root);
        AuthenticationException ex = new AuthenticationException("outer", middle);

        assertEquals(middle, ex.getCause());
        assertEquals(root, ex.getCause().getCause());
    }

    @Test
    void should_beThrowable_when_thrown() {
        assertThrows(AuthenticationException.class, () -> {
            throw new AuthenticationException("thrown");
        });
    }
}
