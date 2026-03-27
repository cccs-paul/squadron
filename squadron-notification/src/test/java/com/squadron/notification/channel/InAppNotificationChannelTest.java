package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationChannelTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private InAppNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new InAppNotificationChannel(messagingTemplate);
    }

    @Test
    void should_returnInApp_when_getChannelTypeCalled() {
        assertEquals("IN_APP", channel.getChannelType());
    }

    @Test
    void should_markAsSent_when_sendCalled() {
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(userId)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("PENDING")
                .build();

        channel.send(notification, null);

        assertEquals("SENT", notification.getStatus());
        assertNotNull(notification.getSentAt());
    }

    @Test
    void should_pushViaWebSocket_when_sendCalled() {
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(userId)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("PENDING")
                .build();

        channel.send(notification, null);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/notifications/" + userId),
                any(Notification.class)
        );
    }

    @Test
    void should_stillMarkAsSent_when_webSocketPushFails() {
        UUID userId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(userId)
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("PENDING")
                .build();

        doThrow(new RuntimeException("WebSocket error"))
                .when(messagingTemplate)
                .convertAndSend(any(String.class), any(Notification.class));

        channel.send(notification, null);

        assertEquals("SENT", notification.getStatus());
        assertNotNull(notification.getSentAt());
    }
}
