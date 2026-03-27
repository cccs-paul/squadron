package com.squadron.notification.service;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRetryService retryService;

    @Test
    void should_retryFailedNotifications() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Test")
                .body("Body")
                .status("FAILED")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                eq("FAILED"), anyInt(), any(Instant.class)))
                .thenReturn(List.of(notification));

        doNothing().when(notificationService).retrySend(notification);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        retryService.retryFailedNotifications();

        assertEquals("SENT", notification.getStatus());
        verify(notificationRepository).save(notification);
        verify(notificationService).retrySend(notification);
    }

    @Test
    void should_moveToDeadLetter_afterMaxRetries() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Test")
                .body("Body")
                .status("FAILED")
                .retryCount(2) // Will become 3 after this failure = MAX_RETRIES
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                eq("FAILED"), anyInt(), any(Instant.class)))
                .thenReturn(List.of(notification));

        doThrow(new RuntimeException("Send failed"))
                .when(notificationService).retrySend(notification);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        retryService.retryFailedNotifications();

        assertEquals("DEAD_LETTER", notification.getStatus());
        assertEquals(3, notification.getRetryCount());
        verify(notificationRepository).save(notification);
    }

    @Test
    void should_skipRetry_whenNoFailedNotifications() {
        when(notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                eq("FAILED"), anyInt(), any(Instant.class)))
                .thenReturn(List.of());

        retryService.retryFailedNotifications();

        verify(notificationService, never()).retrySend(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void should_getDeadLetterNotifications() {
        Notification dl1 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Dead 1")
                .body("Body")
                .status("DEAD_LETTER")
                .retryCount(3)
                .createdAt(Instant.now())
                .build();

        Notification dl2 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("SLACK")
                .subject("Dead 2")
                .body("Body")
                .status("DEAD_LETTER")
                .retryCount(3)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusOrderByCreatedAtDesc("DEAD_LETTER"))
                .thenReturn(List.of(dl1, dl2));

        List<Notification> results = retryService.getDeadLetterNotifications(10);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void should_limitDeadLetterNotifications() {
        Notification dl1 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Dead 1")
                .body("Body")
                .status("DEAD_LETTER")
                .retryCount(3)
                .createdAt(Instant.now())
                .build();

        Notification dl2 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .channel("SLACK")
                .subject("Dead 2")
                .body("Body")
                .status("DEAD_LETTER")
                .retryCount(3)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusOrderByCreatedAtDesc("DEAD_LETTER"))
                .thenReturn(List.of(dl1, dl2));

        // Request limit of 1
        List<Notification> results = retryService.getDeadLetterNotifications(1);

        assertEquals(1, results.size());
    }

    @Test
    void should_handleRetryException_andIncrementCount() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Test")
                .body("Body")
                .status("FAILED")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                eq("FAILED"), anyInt(), any(Instant.class)))
                .thenReturn(List.of(notification));

        doThrow(new RuntimeException("Transient error"))
                .when(notificationService).retrySend(notification);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        retryService.retryFailedNotifications();

        // retryCount incremented but still below MAX_RETRIES, so status stays FAILED
        assertEquals("FAILED", notification.getStatus());
        assertEquals(1, notification.getRetryCount());
        verify(notificationRepository).save(notification);
    }

    @Test
    void should_retryMultipleNotifications() {
        Notification n1 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("First")
                .body("Body")
                .status("FAILED")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        Notification n2 = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("SLACK")
                .subject("Second")
                .body("Body")
                .status("FAILED")
                .retryCount(1)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                eq("FAILED"), anyInt(), any(Instant.class)))
                .thenReturn(List.of(n1, n2));

        doNothing().when(notificationService).retrySend(any());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        retryService.retryFailedNotifications();

        assertEquals("SENT", n1.getStatus());
        assertEquals("SENT", n2.getStatus());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }
}
