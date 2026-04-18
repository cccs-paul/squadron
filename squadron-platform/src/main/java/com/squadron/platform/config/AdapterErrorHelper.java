package com.squadron.platform.config;

import org.slf4j.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Shared error classification logic for platform adapters.
 * Inspects exception cause chains to produce user-friendly error messages.
 */
public final class AdapterErrorHelper {

    private AdapterErrorHelper() {}

    /**
     * Wraps a checked-exception-throwing operation with the standard adapter error handling pattern.
     * Rethrows {@link RuntimeException}s as-is; classifies checked exceptions and wraps them.
     *
     * @param action       the operation to execute
     * @param platform     platform name for error messages (e.g. "GitHub", "Jira Cloud")
     * @param description  what the operation does (e.g. "fetch tasks", "get task")
     * @param log          logger for error messages
     * @param <T>          return type
     * @return the result of the action
     */
    public static <T> T wrapChecked(Callable<T> action, String platform,
                                     String description, Logger log) {
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to {} on {}: {}", description, platform, message, e);
            throw new RuntimeException("Failed to " + description + " on " + platform + ": " + message, e);
        }
    }

    /**
     * Wraps a void checked-exception-throwing operation with standard adapter error handling.
     */
    public static void wrapCheckedVoid(VoidCallable action, String platform,
                                        String description, Logger log) {
        try {
            action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String classified = classifyError(e);
            String message = classified != null ? classified : e.getMessage();
            log.error("Failed to {} on {}: {}", description, platform, message, e);
            throw new RuntimeException("Failed to " + description + " on " + platform + ": " + message, e);
        }
    }

    /**
     * Functional interface for void operations that may throw checked exceptions.
     */
    @FunctionalInterface
    public interface VoidCallable {
        void call() throws Exception;
    }

    /**
     * Extracts a single token value from the credentials map by checking known token field names.
     * Shared by all platform adapters.
     */
    public static String resolveToken(Map<String, String> credentials) {
        for (String key : List.of("accessToken", "pat", "apiKey", "apiToken")) {
            String value = credentials.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Checks whether a response body looks like HTML instead of JSON.
     * Returns a user-friendly message if so, or null if it looks like valid JSON.
     */
    public static String checkForHtmlResponse(String responseBody, Logger log) {
        if (responseBody != null && responseBody.trim().startsWith("<")) {
            String preview = responseBody.substring(0, Math.min(responseBody.length(), 500));
            log.error("Received HTML response instead of JSON. Response preview: {}", preview);
            return "Received HTML instead of JSON — check the base URL and authentication credentials. " +
                    "The server may be returning a login page, error page, or SSO redirect.";
        }
        return null;
    }

    /**
     * Classifies an exception and returns a user-friendly error message.
     * Inspects the full cause chain for known error types.
     */
    public static String classifyError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SSLHandshakeException) {
                return "SSL certificate not trusted — the server may use a self-signed or internal CA certificate. " +
                        "Contact your administrator to add the certificate to the trusted store.";
            }
            if (cause instanceof SSLException) {
                return "SSL/TLS connection error — unable to establish a secure connection to the server.";
            }
            if (cause instanceof UnknownHostException) {
                return "Unable to resolve hostname — check that the base URL is correct and the server is reachable.";
            }
            if (cause instanceof ConnectException) {
                return "Unable to connect to the server — check that the base URL is correct and the server is running.";
            }
            cause = cause.getCause();
        }

        // Check message for common patterns
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("PKIX path building failed") || msg.contains("unable to find valid certification path")) {
                return "SSL certificate not trusted — the server may use a self-signed or internal CA certificate.";
            }
            if (msg.contains("401") || msg.contains("Unauthorized")) {
                return "Authentication failed — check your credentials (API token, PAT, or username/password).";
            }
            if (msg.contains("403") || msg.contains("Forbidden")) {
                return "Access denied — the provided credentials do not have permission to access this resource.";
            }
        }

        return null;
    }
}
