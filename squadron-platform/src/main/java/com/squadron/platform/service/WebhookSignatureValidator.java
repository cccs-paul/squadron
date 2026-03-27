package com.squadron.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates webhook signatures for each supported platform.
 * Uses HMAC-SHA256 for JIRA and GitHub, direct comparison for GitLab,
 * and always accepts Azure DevOps (no standard webhook signature).
 */
@Service
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Validates the webhook signature for the given platform.
     *
     * @param platform        the platform identifier (JIRA, GITHUB, GITLAB, AZURE_DEVOPS)
     * @param webhookSecret   the shared secret for signature computation (may be null)
     * @param signatureHeader the signature header value from the HTTP request
     * @param rawBody         the raw request body bytes
     * @return true if the signature is valid or verification is not applicable
     */
    public boolean validateSignature(String platform, String webhookSecret, String signatureHeader, byte[] rawBody) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Webhook secret is null/empty for platform {}; skipping signature verification", platform);
            return true;
        }

        if (platform == null) {
            log.error("Platform is null; cannot validate signature");
            return false;
        }

        return switch (platform.toUpperCase()) {
            case "JIRA" -> validateHmacSignature(webhookSecret, signatureHeader, rawBody, "JIRA");
            case "GITHUB" -> validateHmacSignature(webhookSecret, signatureHeader, rawBody, "GitHub");
            case "GITLAB" -> validateGitLabToken(webhookSecret, signatureHeader);
            case "AZURE_DEVOPS" -> {
                log.warn("Azure DevOps webhooks do not support signature verification; accepting unverified");
                yield true;
            }
            default -> {
                log.error("Unknown platform for signature validation: {}", platform);
                yield false;
            }
        };
    }

    /**
     * Validates HMAC-SHA256 signature for JIRA and GitHub webhooks.
     * Expected format: "sha256=<hex-encoded-hmac>"
     */
    private boolean validateHmacSignature(String secret, String signatureHeader, byte[] rawBody, String platformName) {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            log.warn("No signature header provided for {} webhook; rejecting", platformName);
            return false;
        }

        String expectedPrefix = "sha256=";
        if (!signatureHeader.startsWith(expectedPrefix)) {
            log.warn("Invalid signature header format for {} webhook: does not start with '{}'", platformName, expectedPrefix);
            return false;
        }

        String providedHex = signatureHeader.substring(expectedPrefix.length());

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] computedHmac = mac.doFinal(rawBody);
            String computedHex = bytesToHex(computedHmac);

            // Constant-time comparison to prevent timing attacks
            boolean valid = MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    providedHex.getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                log.warn("Signature mismatch for {} webhook", platformName);
            }

            return valid;
        } catch (Exception e) {
            log.error("Failed to compute HMAC for {} webhook signature validation", platformName, e);
            return false;
        }
    }

    /**
     * Validates GitLab webhook token via direct string comparison.
     * GitLab sends the secret token in the X-Gitlab-Token header.
     */
    private boolean validateGitLabToken(String secret, String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isEmpty()) {
            log.warn("No X-Gitlab-Token header provided for GitLab webhook; rejecting");
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        boolean valid = MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                tokenHeader.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            log.warn("GitLab token mismatch for webhook");
        }

        return valid;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
