package com.squadron.common.exception;

import com.squadron.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void should_return404_when_resourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "123");

        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("User"));
    }

    @Test
    void should_return401_when_unauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Invalid credentials");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid credentials", response.getBody().getMessage());
    }

    @Test
    void should_return403_when_forbiddenException() {
        ForbiddenException ex = new ForbiddenException("Access denied");

        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void should_return409_when_invalidStateTransitionException() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("OPEN", "DONE");

        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidStateTransition(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void should_return502_when_platformIntegrationException() {
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA", "Connection refused");

        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformIntegration(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Connection refused", response.getBody().getMessage());
    }

    @Test
    void should_return500_when_squadronException() {
        SquadronException ex = new SquadronException("Internal error", "INTERNAL_ERROR");

        ResponseEntity<ApiResponse<Void>> response = handler.handleSquadronException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void should_return500_when_genericException() {
        Exception ex = new RuntimeException("Unexpected");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }
}
