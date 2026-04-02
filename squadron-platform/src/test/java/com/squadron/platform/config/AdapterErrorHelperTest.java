package com.squadron.platform.config;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class AdapterErrorHelperTest {

    private static final Logger log = LoggerFactory.getLogger(AdapterErrorHelperTest.class);

    // --- checkForHtmlResponse ---

    @Test
    void should_detectHtmlResponse_when_startsWithAngleBracket() {
        String html = "<html><body>Login page</body></html>";
        String result = AdapterErrorHelper.checkForHtmlResponse(html, log);
        assertNotNull(result);
        assertTrue(result.contains("Received HTML instead of JSON"));
    }

    @Test
    void should_detectHtmlResponse_when_startsWithDoctype() {
        String html = "<!DOCTYPE html><html><body>Error</body></html>";
        String result = AdapterErrorHelper.checkForHtmlResponse(html, log);
        assertNotNull(result);
        assertTrue(result.contains("Received HTML instead of JSON"));
    }

    @Test
    void should_returnNull_when_validJsonResponse() {
        String json = "{\"key\": \"value\"}";
        String result = AdapterErrorHelper.checkForHtmlResponse(json, log);
        assertNull(result);
    }

    @Test
    void should_returnNull_when_jsonArrayResponse() {
        String json = "[{\"key\": \"value\"}]";
        String result = AdapterErrorHelper.checkForHtmlResponse(json, log);
        assertNull(result);
    }

    @Test
    void should_returnNull_when_nullResponse() {
        String result = AdapterErrorHelper.checkForHtmlResponse(null, log);
        assertNull(result);
    }

    @Test
    void should_detectHtmlResponse_when_whitespaceBeforeAngleBracket() {
        String html = "   <html><body>SSO page</body></html>";
        String result = AdapterErrorHelper.checkForHtmlResponse(html, log);
        assertNotNull(result);
        assertTrue(result.contains("Received HTML instead of JSON"));
    }

    // --- classifyError ---

    @Test
    void should_classifySslHandshakeException() {
        SSLHandshakeException sslEx = new SSLHandshakeException("PKIX path building failed");
        RuntimeException wrapper = new RuntimeException("Request failed", sslEx);
        String result = AdapterErrorHelper.classifyError(wrapper);
        assertNotNull(result);
        assertTrue(result.contains("SSL certificate not trusted"));
    }

    @Test
    void should_classifyUnknownHostException() {
        UnknownHostException unknownHost = new UnknownHostException("nonexistent.server.com");
        RuntimeException wrapper = new RuntimeException("Request failed", unknownHost);
        String result = AdapterErrorHelper.classifyError(wrapper);
        assertNotNull(result);
        assertTrue(result.contains("Unable to resolve hostname"));
    }

    @Test
    void should_classifyConnectException() {
        ConnectException connEx = new ConnectException("Connection refused");
        RuntimeException wrapper = new RuntimeException("Request failed", connEx);
        String result = AdapterErrorHelper.classifyError(wrapper);
        assertNotNull(result);
        assertTrue(result.contains("Unable to connect to the server"));
    }

    @Test
    void should_classifyPkixMessagePattern() {
        RuntimeException ex = new RuntimeException("PKIX path building failed: unable to find valid certification path");
        String result = AdapterErrorHelper.classifyError(ex);
        assertNotNull(result);
        assertTrue(result.contains("SSL certificate not trusted"));
    }

    @Test
    void should_classify401Unauthorized() {
        RuntimeException ex = new RuntimeException("401 Unauthorized");
        String result = AdapterErrorHelper.classifyError(ex);
        assertNotNull(result);
        assertTrue(result.contains("Authentication failed"));
    }

    @Test
    void should_classify403Forbidden() {
        RuntimeException ex = new RuntimeException("403 Forbidden");
        String result = AdapterErrorHelper.classifyError(ex);
        assertNotNull(result);
        assertTrue(result.contains("Access denied"));
    }

    @Test
    void should_returnNull_when_unclassifiedError() {
        RuntimeException ex = new RuntimeException("Some random error");
        String result = AdapterErrorHelper.classifyError(ex);
        assertNull(result);
    }
}
