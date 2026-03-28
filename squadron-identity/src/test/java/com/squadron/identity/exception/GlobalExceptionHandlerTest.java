package com.squadron.identity.exception;

import com.squadron.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void should_return404_when_resourceNotFoundExceptionHandled() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Tenant", "id", "abc-123");

        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("Tenant"));
    }

    @Test
    void should_return401_when_authenticationExceptionHandled() {
        AuthenticationException ex = new AuthenticationException("Invalid token");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid token", response.getBody().getMessage());
    }

    @Test
    void should_return400_when_illegalArgumentExceptionHandled() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Bad argument", response.getBody().getMessage());
    }

    @Test
    void should_return400_when_validationExceptionHandled() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "name", "must not be blank"));

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandler.class.getDeclaredMethod("handleValidation", MethodArgumentNotValidException.class), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("name"));
        assertTrue(response.getBody().getMessage().contains("must not be blank"));
    }

    @Test
    void should_return400WithMultipleErrors_when_multipleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "name", "must not be blank"));
        bindingResult.addError(new FieldError("object", "email", "must be a valid email"));

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandler.class.getDeclaredMethod("handleValidation", MethodArgumentNotValidException.class), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      ));
        assertNotNull(response.getBody());
        String message = response.getBody().getMessage();
        assertTrue(message.contains("name"));
        assertTrue(message.contains("email"));
        assertTrue(message.contains(";"));
    }

    @Test
    void should_return500_when_generalExceptionHandled() {
        Exception ex = new Exception("Unexpected error occurred");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("Internal server error"));
        assertTrue(response.getBody().getMessage().contains("Unexpected error occurred"));
    }

    @Test
    void should_haveRestControllerAdviceAnnotation_when_checked() {
        assertTrue(IdentityExceptionHandler.class.isAnnotationPresent(
                org.springframework.web.bind.annotation.RestControllerAdvice.class));
    }
}
