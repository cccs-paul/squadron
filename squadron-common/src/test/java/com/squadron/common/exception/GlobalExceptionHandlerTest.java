package com.squadron.common.exception;

import com.squadron.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

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
    void should_return403_when_accessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Not authorized");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void should_return400_when_validationFails() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "email", "must be a valid email"));

        MethodParameter methodParam = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParam, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("name"));
        assertTrue(response.getBody().getMessage().contains("must not be blank"));
        assertTrue(response.getBody().getMessage().contains("email"));
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

    @Test
    void should_return400_when_typeMismatchException() throws Exception {
        MethodParameter methodParam = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethodUuid", UUID.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "demo-user-001", UUID.class, "userId", methodParam, new IllegalArgumentException("Invalid UUID"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("userId"));
        assertTrue(response.getBody().getMessage().contains("demo-user-001"));
        assertTrue(response.getBody().getMessage().contains("UUID"));
    }

    @Test
    void should_return502WithSslMessage_when_sslHandshakeException() {
        javax.net.ssl.SSLHandshakeException sslEx = new javax.net.ssl.SSLHandshakeException("PKIX path building failed");
        RuntimeException cause = new RuntimeException("Request failed", sslEx);
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA_SERVER", "Failed to fetch projects: Request failed: PKIX path building failed", cause);

        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformIntegration(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("SSL certificate not trusted"));
    }

    @Test
    void should_return502WithHtmlMessage_when_htmlResponseDetected() {
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA_CLOUD",
                "Failed to fetch projects: Received HTML instead of JSON — check the base URL");

        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformIntegration(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Received HTML instead of JSON"));
    }

    @Test
    void should_return502WithUnknownHostMessage_when_dnsFailure() {
        java.net.UnknownHostException dnsEx = new java.net.UnknownHostException("nonexistent.server.com");
        PlatformIntegrationException ex = new PlatformIntegrationException("GITHUB",
                "Failed to fetch projects: nonexistent.server.com", dnsEx);

        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformIntegration(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Unable to resolve hostname"));
    }

    @Test
    void should_return502WithSimplifiedMessage_when_nestedErrorChain() {
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA_SERVER",
                "Failed to fetch projects: Failed to fetch projects from Jira Server: Connection timeout");

        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformIntegration(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        // Should simplify the message
        assertNotNull(response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().isEmpty());
    }

    // Dummy method used to create a MethodParameter for MethodArgumentNotValidException
    @SuppressWarnings("unused")
    private void dummyMethod(String param) {}

    // Dummy method used to create a MethodParameter for MethodArgumentTypeMismatchException
    @SuppressWarnings("unused")
    private void dummyMethodUuid(UUID param) {}
}
