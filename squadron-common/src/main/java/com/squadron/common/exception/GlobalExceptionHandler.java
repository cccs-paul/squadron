package com.squadron.common.exception;

import com.squadron.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for parameter '%s': expected type %s",
                ex.getValue(), ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStateTransition(InvalidStateTransitionException ex) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PlatformIntegrationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatformIntegration(PlatformIntegrationException ex) {
        log.error("Platform integration error [{}]: {}", ex.getPlatform(), ex.getMessage(), ex);
        String userMessage = extractUserFriendlyMessage(ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(userMessage));
    }

    /**
     * Extracts a user-friendly message from a PlatformIntegrationException by inspecting
     * the exception cause chain for known error patterns.
     */
    private String extractUserFriendlyMessage(PlatformIntegrationException ex) {
        String message = ex.getMessage();
        Throwable cause = ex.getCause();

        // Check for SSL/certificate errors in the cause chain
        while (cause != null) {
            String causeMsg = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            if (cause instanceof javax.net.ssl.SSLHandshakeException
                    || (cause.getMessage() != null && cause.getMessage().contains("PKIX path building failed"))) {
                return "SSL certificate not trusted — the server may use a self-signed or internal CA certificate. "
                        + "Contact your administrator to add the certificate to the trusted store.";
            }
            if (cause instanceof javax.net.ssl.SSLException) {
                return "SSL/TLS connection error — unable to establish a secure connection to the server.";
            }
            if (cause instanceof java.net.UnknownHostException) {
                return "Unable to resolve hostname — check that the base URL is correct and the server is reachable.";
            }
            if (cause instanceof java.net.ConnectException) {
                return "Unable to connect to the server — check that the base URL is correct and the server is running.";
            }
            cause = cause.getCause();
        }

        // Check for known patterns in the message
        if (message != null) {
            if (message.contains("Received HTML instead of JSON")) {
                return "Received HTML instead of JSON from the platform — check the base URL and authentication credentials.";
            }
            if (message.contains("SSL certificate not trusted")) {
                return "SSL certificate not trusted — the server may use a self-signed or internal CA certificate.";
            }
            if (message.contains("Authentication failed") || message.contains("401")) {
                return "Authentication failed — check your credentials.";
            }
            if (message.contains("Access denied") || message.contains("403")) {
                return "Access denied — the credentials do not have permission to access this resource.";
            }
        }

        // Simplify nested "Failed to X: Failed to X from Y: actual error" messages
        if (message != null && message.contains(": ")) {
            // Take the last meaningful segment
            String[] parts = message.split(": ");
            String lastPart = parts[parts.length - 1].trim();
            if (!lastPart.isEmpty() && lastPart.length() > 5) {
                return "Platform integration error: " + lastPart;
            }
        }

        return message != null ? message : "An error occurred communicating with the external platform.";
    }

    @ExceptionHandler(SquadronException.class)
    public ResponseEntity<ApiResponse<Void>> handleSquadronException(SquadronException ex) {
        log.error("Squadron error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
