package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlackNotificationChannelTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private SlackNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new SlackNotificationChannel(webClientBuilder);
    }

    @Test
    void should_returnSlack_when_getChannelTypeCalled() {
        assertEquals("SLACK", channel.getChannelType());
    }

    @Test
    void should_markAsFailed_when_noWebhookUrl() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .build();

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
        assertEquals("No Slack webhook URL configured", notification.getErrorMessage());
    }

    @Test
    void should_markAsFailed_when_preferenceIsNull() {
        Notification notification = createNotification();

        channel.send(notification, null);

        assertEquals("FAILED", notification.getStatus());
    }

    @Test
    void should_markAsFailed_when_webhookUrlIsBlank() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .slackWebhookUrl("  ")
                .build();

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_sendSlackMessage_when_validWebhookUrl() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .slackWebhookUrl("https://hooks.slack.com/services/test")
                .build();

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        channel.send(notification, preference);

        assertEquals("SENT", notification.getStatus());
        assertNotNull(notification.getSentAt());
    }

    @Test
    void should_markAsFailed_when_webhookCallFails() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .slackWebhookUrl("https://hooks.slack.com/services/test")
                .build();

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenThrow(new RuntimeException("Connection refused"));

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
        assertNotNull(notification.getErrorMessage());
    }

    private Notification createNotification() {
        return Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("SLACK")
                .subject("Test Subject")
                .body("Test Body")
                .status("PENDING")
                .build();
    }
}
