package com.squadron.identity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourceNotFoundExceptionTest {

    @Test
    void should_createException_when_messageProvided() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

        assertEquals("Resource not found", ex.getMessage());
    }

    @Test
    void should_createFormattedMessage_when_resourceFieldValueProvided() {
        UUID id = UUID.randomUUID();
        ResourceNotFoundException ex = new ResourceNotFoundException("Tenant", "id", id);

        assertEquals(String.format("Tenant not found with id: '%s'", id), ex.getMessage());
    }

    @Test
    void should_createFormattedMessage_when_stringFieldValue() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "email", "test@example.com");

        assertEquals("User not found with email: 'test@example.com'", ex.getMessage());
    }

    @Test
    void should_beRuntimeException_when_checked() {
        ResourceNotFoundException ex = new ResourceNotFoundException("test");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void should_haveNotFoundResponseStatus_when_checked() {
        ResponseStatus annotation = ResourceNotFoundException.class.getAnnotation(ResponseStatus.class);

        assertNotNull(annotation);
        assertEquals(HttpStatus.NOT_FOUND, annotation.value());
    }

    @Test
    void should_beThrowable_when_thrown() {
        assertThrows(ResourceNotFoundException.class, () -> {
            throw new ResourceNotFoundException("Tenant", "slug", "acme");
        });
    }

    @Test
    void should_includeResourceName_when_formattedMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Team", "name", "backend");

        assertTrue(ex.getMessage().contains("Team"));
        assertTrue(ex.getMessage().contains("name"));
        assertTrue(ex.getMessage().contains("backend"));
    }

    @Test
    void should_handleNullFieldValue_when_formattedMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Config", "key", null);

        assertTrue(ex.getMessage().contains("null"));
    }
}
