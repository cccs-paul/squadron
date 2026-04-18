package com.squadron.notification.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookUrlValidatorTest {

    // --- validateWebhookUrl ---

    @Test
    void should_reject_null_url() {
        assertThrows(IllegalArgumentException.class, () -> WebhookUrlValidator.validateWebhookUrl(null));
    }

    @Test
    void should_reject_blank_url() {
        assertThrows(IllegalArgumentException.class, () -> WebhookUrlValidator.validateWebhookUrl("  "));
    }

    @Test
    void should_reject_http_scheme() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("http://hooks.slack.com/services/test"));
    }

    @Test
    void should_reject_localhost() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://localhost/test"));
    }

    @Test
    void should_reject_dotLocal_domain() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://myhost.local/test"));
    }

    @Test
    void should_accept_valid_https_url() {
        assertDoesNotThrow(() -> WebhookUrlValidator.validateWebhookUrl("https://hooks.slack.com/services/test"));
    }

    // --- validateSlackWebhookUrl ---

    @Test
    void should_accept_valid_slack_url() {
        assertDoesNotThrow(
                () -> WebhookUrlValidator.validateSlackWebhookUrl("https://hooks.slack.com/services/T00/B00/xxx"));
    }

    @Test
    void should_reject_non_slack_host() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateSlackWebhookUrl("https://evil.com/services/test"));
    }

    @Test
    void should_reject_http_slack_url() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateSlackWebhookUrl("http://hooks.slack.com/services/test"));
    }

    // --- validateTeamsWebhookUrl ---

    @Test
    void should_accept_valid_teams_office_url() {
        assertDoesNotThrow(
                () -> WebhookUrlValidator.validateTeamsWebhookUrl("https://outlook.webhook.office.com/test"));
    }

    @Test
    void should_accept_valid_teams_azure_url() {
        assertDoesNotThrow(
                () -> WebhookUrlValidator.validateTeamsWebhookUrl("https://prod-00.logic.azure.com/workflows/test"));
    }

    @Test
    void should_reject_non_teams_host() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateTeamsWebhookUrl("https://evil.com/test"));
    }

    @Test
    void should_reject_http_teams_url() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateTeamsWebhookUrl("http://outlook.webhook.office.com/test"));
    }

    // --- Private IP rejection ---

    @Test
    void should_reject_url_with_10_x_ip() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://10.0.0.1/test"));
    }

    @Test
    void should_reject_url_with_172_16_ip() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://172.16.0.1/test"));
    }

    @Test
    void should_reject_url_with_192_168_ip() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://192.168.1.1/test"));
    }

    @Test
    void should_reject_url_with_127_ip() {
        assertThrows(IllegalArgumentException.class,
                () -> WebhookUrlValidator.validateWebhookUrl("https://127.0.0.1/test"));
    }
}
