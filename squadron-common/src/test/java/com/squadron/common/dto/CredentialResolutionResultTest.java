package com.squadron.common.dto;

import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CredentialResolutionResultTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        Instant expires = Instant.now().plusSeconds(3600);

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("ghp_abc123")
                .sshPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nMIIE...")
                .credentialType(CredentialType.OAUTH2)
                .expiresAt(expires)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        assertEquals("ghp_abc123", result.getAccessToken());
        assertEquals("-----BEGIN RSA PRIVATE KEY-----\nMIIE...", result.getSshPrivateKey());
        assertEquals(CredentialType.OAUTH2, result.getCredentialType());
        assertEquals(expires, result.getExpiresAt());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        CredentialResolutionResult result = new CredentialResolutionResult();

        assertNull(result.getAccessToken());
        assertNull(result.getSshPrivateKey());
        assertNull(result.getCredentialType());
        assertNull(result.getExpiresAt());
        assertNull(result.getGitAuthMode());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        Instant expires = Instant.now().plusSeconds(3600);

        CredentialResolutionResult result = new CredentialResolutionResult(
                "token123", "ssh-key-data", CredentialType.PAT, expires, GitAuthMode.SSH_KEY
        );

        assertEquals("token123", result.getAccessToken());
        assertEquals("ssh-key-data", result.getSshPrivateKey());
        assertEquals(CredentialType.PAT, result.getCredentialType());
        assertEquals(expires, result.getExpiresAt());
        assertEquals(GitAuthMode.SSH_KEY, result.getGitAuthMode());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        CredentialResolutionResult result = new CredentialResolutionResult();
        Instant expires = Instant.now().plusSeconds(7200);

        result.setAccessToken("my-token");
        result.setSshPrivateKey("my-ssh-key");
        result.setCredentialType(CredentialType.GITHUB_APP);
        result.setExpiresAt(expires);
        result.setGitAuthMode(GitAuthMode.HTTPS_TOKEN);

        assertEquals("my-token", result.getAccessToken());
        assertEquals("my-ssh-key", result.getSshPrivateKey());
        assertEquals(CredentialType.GITHUB_APP, result.getCredentialType());
        assertEquals(expires, result.getExpiresAt());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        Instant expires = Instant.now().plusSeconds(3600);

        CredentialResolutionResult result1 = CredentialResolutionResult.builder()
                .accessToken("token")
                .credentialType(CredentialType.PAT)
                .expiresAt(expires)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        CredentialResolutionResult result2 = CredentialResolutionResult.builder()
                .accessToken("token")
                .credentialType(CredentialType.PAT)
                .expiresAt(expires)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        CredentialResolutionResult result1 = CredentialResolutionResult.builder()
                .accessToken("token-a")
                .credentialType(CredentialType.PAT)
                .build();

        CredentialResolutionResult result2 = CredentialResolutionResult.builder()
                .accessToken("token-b")
                .credentialType(CredentialType.OAUTH2)
                .build();

        assertNotEquals(result1, result2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("ghp_abc123")
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        String str = result.toString();
        assertTrue(str.contains("ghp_abc123"));
        assertTrue(str.contains("OAUTH2"));
        assertTrue(str.contains("HTTPS_TOKEN"));
    }

    @Test
    void should_handleNullExpiresAt_when_patCredential() {
        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("pat-token")
                .credentialType(CredentialType.PAT)
                .expiresAt(null)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        assertNull(result.getExpiresAt());
        assertEquals(CredentialType.PAT, result.getCredentialType());
    }

    @Test
    void should_handleNullSshKey_when_httpsMode() {
        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("oauth-token")
                .sshPrivateKey(null)
                .credentialType(CredentialType.OAUTH2)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        assertNull(result.getSshPrivateKey());
        assertEquals(GitAuthMode.HTTPS_TOKEN, result.getGitAuthMode());
    }
}
