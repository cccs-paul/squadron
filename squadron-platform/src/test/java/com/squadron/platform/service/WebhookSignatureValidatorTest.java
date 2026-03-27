package com.squadron.platform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
    }

    // --- GitHub HMAC-SHA256 ---

    @Test
    void should_validateGitHubSignature_when_validHmac() throws Exception {
        String secret = "my-github-secret";
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmacSha256(secret, body);

        boolean result = validator.validateSignature("GITHUB", secret, signature, body);

        assertThat(result).isTrue();
    }

    @Test
    void should_rejectGitHubSignature_when_invalidHmac() {
        String secret = "my-github-secret";
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        boolean result = validator.validateSignature("GITHUB", secret, signature, body);

        assertThat(result).isFalse();
    }

    // --- JIRA HMAC-SHA256 ---

    @Test
    void should_validateJiraSignature_when_validHmac() throws Exception {
        String secret = "my-jira-secret";
        byte[] body = "{\"webhookEvent\":\"jira:issue_updated\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmacSha256(secret, body);

        boolean result = validator.validateSignature("JIRA", secret, signature, body);

        assertThat(result).isTrue();
    }

    @Test
    void should_rejectJiraSignature_when_invalidHmac() {
        String secret = "my-jira-secret";
        byte[] body = "{\"webhookEvent\":\"jira:issue_updated\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

        boolean result = validator.validateSignature("JIRA", secret, signature, body);

        assertThat(result).isFalse();
    }

    // --- GitLab Token ---

    @Test
    void should_validateGitLabToken_when_matching() {
        String secret = "my-gitlab-token";
        byte[] body = "{\"object_kind\":\"issue\"}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITLAB", secret, secret, body);

        assertThat(result).isTrue();
    }

    @Test
    void should_rejectGitLabToken_when_notMatching() {
        String secret = "my-gitlab-token";
        byte[] body = "{\"object_kind\":\"issue\"}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITLAB", secret, "wrong-token", body);

        assertThat(result).isFalse();
    }

    // --- Azure DevOps ---

    @Test
    void should_acceptAzureDevOps_when_noSignatureRequired() {
        byte[] body = "{\"eventType\":\"workitem.updated\"}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("AZURE_DEVOPS", "some-secret", null, body);

        assertThat(result).isTrue();
    }

    // --- Null/empty secret ---

    @Test
    void should_returnTrue_when_webhookSecretIsNull() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITHUB", null, "sha256=abc", body);

        assertThat(result).isTrue();
    }

    @Test
    void should_returnTrue_when_webhookSecretIsEmpty() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITHUB", "", "sha256=abc", body);

        assertThat(result).isTrue();
    }

    // --- Unknown platform ---

    @Test
    void should_returnFalse_when_unknownPlatform() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("UNKNOWN_PLATFORM", "secret", "sig", body);

        assertThat(result).isFalse();
    }

    // --- Edge cases ---

    @Test
    void should_rejectGitHubSignature_when_noSignatureHeader() {
        String secret = "my-github-secret";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITHUB", secret, null, body);

        assertThat(result).isFalse();
    }

    @Test
    void should_rejectGitHubSignature_when_emptySignatureHeader() {
        String secret = "my-github-secret";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITHUB", secret, "", body);

        assertThat(result).isFalse();
    }

    @Test
    void should_rejectGitHubSignature_when_missingPrefix() {
        String secret = "my-github-secret";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITHUB", secret, "abcdef1234567890", body);

        assertThat(result).isFalse();
    }

    @Test
    void should_rejectGitLabToken_when_nullHeader() {
        String secret = "my-gitlab-token";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        boolean result = validator.validateSignature("GITLAB", secret, null, body);

        assertThat(result).isFalse();
    }

    /**
     * Helper to compute HMAC-SHA256 and return hex-encoded string.
     */
    private String computeHmacSha256(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmac = mac.doFinal(data);
        StringBuilder sb = new StringBuilder(hmac.length * 2);
        for (byte b : hmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
