package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialTypeTest {

    @Test
    void should_haveOauth2Value_when_enumDefined() {
        assertEquals("OAUTH2", CredentialType.OAUTH2.name());
    }

    @Test
    void should_havePatValue_when_enumDefined() {
        assertEquals("PAT", CredentialType.PAT.name());
    }

    @Test
    void should_haveDeployKeyValue_when_enumDefined() {
        assertEquals("DEPLOY_KEY", CredentialType.DEPLOY_KEY.name());
    }

    @Test
    void should_haveGithubAppValue_when_enumDefined() {
        assertEquals("GITHUB_APP", CredentialType.GITHUB_APP.name());
    }

    @Test
    void should_haveFourValues_when_enumDefined() {
        CredentialType[] values = CredentialType.values();

        assertEquals(4, values.length);
    }

    @Test
    void should_returnCorrectEnum_when_valueOfCalled() {
        assertEquals(CredentialType.OAUTH2, CredentialType.valueOf("OAUTH2"));
        assertEquals(CredentialType.PAT, CredentialType.valueOf("PAT"));
        assertEquals(CredentialType.DEPLOY_KEY, CredentialType.valueOf("DEPLOY_KEY"));
        assertEquals(CredentialType.GITHUB_APP, CredentialType.valueOf("GITHUB_APP"));
    }

    @Test
    void should_throwIllegalArgumentException_when_invalidValueOfCalled() {
        assertThrows(IllegalArgumentException.class, () -> CredentialType.valueOf("UNKNOWN"));
    }

    @Test
    void should_haveCorrectOrdinalOrder_when_enumDefined() {
        assertTrue(CredentialType.OAUTH2.ordinal() < CredentialType.PAT.ordinal());
        assertTrue(CredentialType.PAT.ordinal() < CredentialType.DEPLOY_KEY.ordinal());
        assertTrue(CredentialType.DEPLOY_KEY.ordinal() < CredentialType.GITHUB_APP.ordinal());
    }
}
