package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmailNotificationChannel(mailSender);
    }

    @Test
    void should_returnEmail_when_getChannelTypeCalled() {
        assertEquals("EMAIL", channel.getChannelType());
    }

    @Test
    void should_markAsFailed_when_noEmailAddress() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .build();

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
        assertEquals("No email address configured", notification.getErrorMessage());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void should_markAsFailed_when_preferenceIsNull() {
        Notification notification = createNotification();

        channel.send(notification, null);

        assertEquals("FAILED", notification.getStatus());
        assertEquals("No email address configured", notification.getErrorMessage());
    }

    @Test
    void should_markAsFailed_when_emailAddressIsBlank() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .emailAddress("   ")
                .build();

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
    }

    @Test
    void should_sendEmail_when_validEmailAddress() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .emailAddress("user@example.com")
                .build();

        channel.send(notification, preference);

        assertEquals("SENT", notification.getStatus());
        assertNotNull(notification.getSentAt());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertEquals("user@example.com", sentMessage.getTo()[0]);
        assertEquals("Test Subject", sentMessage.getSubject());
        assertEquals("Test Body", sentMessage.getText());
    }

    @Test
    void should_markAsFailed_when_mailSenderThrows() {
        Notification notification = createNotification();
        NotificationPreference preference = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .emailAddress("user@example.com")
                .build();

        doThrow(new RuntimeException("SMTP error"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        channel.send(notification, preference);

        assertEquals("FAILED", notification.getStatus());
        assertNotNull(notification.getErrorMessage());
        assertEquals("Email delivery failed: SMTP error", notification.getErrorMessage());
    }

    private Notification createNotification() {
        return Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Test Subject")
                .body("Test Body")
                .status("PENDING")
                .build();
    }
}
