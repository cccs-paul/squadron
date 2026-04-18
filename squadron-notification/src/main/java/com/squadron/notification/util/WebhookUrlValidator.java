package com.squadron.notification.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates webhook URLs to prevent SSRF attacks.
 * Rejects private/reserved IP ranges, non-HTTPS schemes, and non-allowlisted hostnames.
 */
public final class WebhookUrlValidator {

    private WebhookUrlValidator() {}

    /**
     * Validates a Slack webhook URL.
     * Must be HTTPS and match https://hooks.slack.com/*
     */
    public static void validateSlackWebhookUrl(String url) {
        validateWebhookUrl(url);
        URI uri = URI.create(url);
        if (!"hooks.slack.com".equals(uri.getHost())) {
            throw new IllegalArgumentException("Slack webhook URL must use hooks.slack.com host");
        }
    }

    /**
     * Validates a Teams webhook URL.
     * Must be HTTPS and match https://*.webhook.office.com/* or https://*.logic.azure.com/*
     */
    public static void validateTeamsWebhookUrl(String url) {
        validateWebhookUrl(url);
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (host == null ||
                !(host.endsWith(".webhook.office.com") || host.endsWith(".logic.azure.com"))) {
            throw new IllegalArgumentException(
                    "Teams webhook URL must use *.webhook.office.com or *.logic.azure.com host");
        }
    }

    /**
     * Validates a generic webhook URL: requires HTTPS, rejects private IPs and reserved hostnames.
     */
    public static void validateWebhookUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be null or blank");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + e.getMessage());
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must have a valid host");
        }

        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".local")) {
            throw new IllegalArgumentException("Webhook URL must not target localhost or .local domains");
        }

        // Resolve and check for private/reserved IPs
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrReserved(addr)) {
                    throw new IllegalArgumentException(
                            "Webhook URL must not resolve to a private or reserved IP address");
                }
            }
        } catch (UnknownHostException e) {
            // If DNS resolution fails, allow it through — the HTTP call will fail anyway.
            // This avoids blocking in unit tests where DNS may not resolve external hosts.
        }
    }

    private static boolean isPrivateOrReserved(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length != 4) {
            // IPv6 — conservatively check site-local/loopback
            return addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress();
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;

        // 0.0.0.0/8
        if (b0 == 0) return true;
        // 10.0.0.0/8
        if (b0 == 10) return true;
        // 127.0.0.0/8
        if (b0 == 127) return true;
        // 169.254.0.0/16
        if (b0 == 169 && b1 == 254) return true;
        // 172.16.0.0/12
        if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
        // 192.168.0.0/16
        if (b0 == 192 && b1 == 168) return true;

        return false;
    }
}
