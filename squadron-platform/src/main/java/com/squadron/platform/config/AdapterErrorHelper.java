package com.squadron.platform.config;

import org.slf4j.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * Shared error classification logic for platform adapters.
 * Inspects exception cause chains to produce user-friendly error messages.
 */
public final class AdapterErrorHelper {

    private AdapterErrorHelper() {}

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
