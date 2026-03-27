package com.squadron.notification.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.notification.channel.NotificationChannel;
import com.squadron.notification.channel.NotificationChannelRegistry;
import com.squadron.notification.dto.NotificationDto;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import com.squadron.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private NotificationChannelRegistry channelRegistry;

    @Mock
    private NotificationChannel notificationChannel;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, preferenceRepository, channelRegistry);
    }

    @Test
    void should_sendNotification_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SendNotificationRequest request = SendNotificationRequest.builder()
                .tenantId(tenantId)
                .userId(userId)
                .channel("IN_APP")
                .subject("Test Subject")
                .body("Test Body")
                .eventType("TASK_STATE_CHANGED")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(channelRegistry.getChannel("IN_APP")).thenReturn(notificationChannel);

        NotificationDto result = notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals(userId, result.getUserId());
        assertEquals("IN_APP", result.getChannel());
        assertEquals("Test Subject", result.getSubject());
        assertEquals("Test Body", result.getBody());

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(notificationChannel).send(any(Notification.class), any());
    }

    @Test
    void should_markAsFailed_when_unknownChannel() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("UNKNOWN")
                .subject("Test")
                .body("Body")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });
        when(preferenceRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(channelRegistry.getChannel("UNKNOWN"))
                .thenThrow(new IllegalArgumentException("Unknown channel type: UNKNOWN"));

        NotificationDto result = notificationService.sendNotification(request);

        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void should_getNotification_when_exists() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        NotificationDto result = notificationService.getNotification(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("SENT", result.getStatus());
    }

    @Test
    void should_throwNotFound_when_notificationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.getNotification(id));
    }

    @Test
    void should_listNotifications_when_called() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).userId(userId)
                .channel("IN_APP").subject("First").body("Body1").status("SENT")
                .createdAt(Instant.now()).build();
        Notification n2 = Notification.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).userId(userId)
                .channel("EMAIL").subject("Second").body("Body2").status("PENDING")
                .createdAt(Instant.now()).build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(n1, n2));

        List<NotificationDto> results = notificationService.listNotifications(userId);

        assertEquals(2, results.size());
    }

    @Test
    void should_listUnread_when_called() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).userId(userId)
                .channel("IN_APP").subject("Unread").body("Body").status("SENT")
                .createdAt(Instant.now()).build();

        when(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "SENT"))
                .thenReturn(List.of(n1));

        List<NotificationDto> results = notificationService.listUnread(userId);

        assertEquals(1, results.size());
        assertEquals("SENT", results.get(0).getStatus());
    }

    @Test
    void should_countUnread_when_called() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUserIdAndStatus(userId, "SENT")).thenReturn(5L);

        long count = notificationService.countUnread(userId);

        assertEquals(5L, count);
    }

    @Test
    void should_markAsRead_when_notificationExists() {
        UUID id = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("SENT")
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDto result = notificationService.markAsRead(id);

        assertEquals("READ", result.getStatus());
        assertNotNull(result.getReadAt());
    }

    @Test
    void should_throwNotFound_when_markingNonexistentAsRead() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(id));
    }

    @Test
    void should_markAllRead_when_called() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).userId(userId)
                .channel("IN_APP").subject("First").body("Body1").status("SENT")
                .createdAt(Instant.now()).build();
        Notification n2 = Notification.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).userId(userId)
                .channel("IN_APP").subject("Second").body("Body2").status("SENT")
                .createdAt(Instant.now()).build();

        when(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "SENT"))
                .thenReturn(List.of(n1, n2));

        notificationService.markAllRead(userId);

        assertEquals("READ", n1.getStatus());
        assertEquals("READ", n2.getStatus());
        assertNotNull(n1.getReadAt());
        assertNotNull(n2.getReadAt());
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    @Test
    void should_handleEmptyUnreadList_when_markAllRead() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "SENT"))
                .thenReturn(List.of());

        notificationService.markAllRead(userId);

        verify(notificationRepository).saveAll(List.of());
    }

    @Test
    void should_retrySend_when_channelExists() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("IN_APP")
                .subject("Test")
                .body("Body")
                .status("FAILED")
                .build();

        when(channelRegistry.getChannel("IN_APP")).thenReturn(notificationChannel);

        notificationService.retrySend(notification);

        verify(notificationChannel).send(notification, null);
    }

    @Test
    void should_throwException_when_retrySendWithUnknownChannel() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("UNKNOWN")
                .subject("Test")
                .body("Body")
                .status("FAILED")
                .build();

        when(channelRegistry.getChannel("UNKNOWN")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> notificationService.retrySend(notification));
    }

    @Test
    void should_sendNotification_when_userIdIsNull() {
        UUID tenantId = UUID.randomUUID();

        SendNotificationRequest request = SendNotificationRequest.builder()
                .tenantId(tenantId)
                .userId(null) // tenant-level notification
                .channel("IN_APP")
                .subject("Test Subject")
                .body("Test Body")
                .eventType("REVIEW_UPDATED")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    if (n.getId() == null) {
                        n.setId(UUID.randomUUID());
                    }
                    return n;
                });
        when(channelRegistry.getChannel("IN_APP")).thenReturn(notificationChannel);

        NotificationDto result = notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals("IN_APP", result.getChannel());
        // Should NOT look up preferences when userId is null
        verify(preferenceRepository, never()).findByUserId(any());
    }
}
