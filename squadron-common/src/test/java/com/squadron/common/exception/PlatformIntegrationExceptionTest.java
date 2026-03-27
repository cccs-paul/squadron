package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformIntegrationExceptionTest {

    @Test
    void should_setPlatformAndMessage_when_twoArgConstructorUsed() {
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA", "Connection refused");

        assertEquals("JIRA", ex.getPlatform());
        assertEquals("Connection refused", ex.getMessage());
    }

    @Test
    void should_setErrorCodeToPlatformError_when_created() {
        PlatformIntegrationException ex = new PlatformIntegrationException("GitHub", "Rate limited");

        assertEquals("PLATFORM_ERROR", ex.getErrorCode());
    }

    @Test
    void should_setPlatformMessageAndCause_when_threeArgConstructorUsed() {
        Throwable cause = new RuntimeException("network error");
        PlatformIntegrationException ex = new PlatformIntegrationException("GitLab", "API failure", cause);

        assertEquals("GitLab", ex.getPlatform());
        assertEquals("API failure", ex.getMessage());
        assertEquals("PLATFORM_ERROR", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void should_haveNullCause_when_twoArgConstructorUsed() {
        PlatformIntegrationException ex = new PlatformIntegrationException("Bitbucket", "Timeout");

        assertNull(ex.getCause());
    }

    @Test
    void should_beSquadronException_when_created() {
        PlatformIntegrationException ex = new PlatformIntegrationException("JIRA", "error");

        assertInstanceOf(SquadronException.class, ex);
    }

    @Test
    void should_preservePlatformName_when_differentPlatformsUsed() {
        PlatformIntegrationException jira = new PlatformIntegrationException("JIRA", "err1");
        PlatformIntegrationException github = new PlatformIntegrationException("GitHub", "err2");
        PlatformIntegrationException azdo = new PlatformIntegrationException("AzureDevOps", "err3");

        assertEquals("JIRA", jira.getPlatform());
        assertEquals("GitHub", github.getPlatform());
        assertEquals("AzureDevOps", azdo.getPlatform());
    }
}
