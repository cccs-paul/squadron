package com.squadron.notification.service;

import com.squadron.notification.channel.NotificationChannel;
import com.squadron.notification.channel.NotificationChannelRegistry;
import com.squadron.notification.dto.NotificationDto;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import com.squadron.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationChannelRegistry channelRegistry;

    public NotificationDto sendNotification(SendNotificationRequest request) {
        log.info("Sending {} notification{} for event {}",
                request.getChannel(),
                request.getUserId() != null ? " to user " + request.getUserId() : " (tenant-level)",
                request.getEventType());

        Notification notification = Notification.builder()
                .tenantId(request.getTenantId())
                .userId(request.getUserId())
                .channel(request.getChannel())
                .subject(request.getSubject())
                .body(request.getBody())
                .relatedTaskId(request.getRelatedTaskId())
                .eventType(request.getEventType())
                .status("PENDING")
                .build();

        notification = notificationRepository.save(notification);

        NotificationPreference preference = null;
        if (request.getUserId() != null) {
            preference = preferenceRepository
                    .findByUserId(request.getUserId())
                    .orElse(null);
        }

        try {
            NotificationChannel channel = channelRegistry.getChannel(request.getChannel());
            channel.send(notification, preference);
        } catch (IllegalArgumentException e) {
            log.error("Unknown channel type: {}", request.getChannel());
            notification.setStatus("FAILED");
            notification.setErrorMessage("Unknown channel type: " + request.getChannel());
        } catch (Exception e) {
            log.error("Failed to send notification {}: {}", notification.getId(), e.getMessage(), e);
            notification.setStatus("FAILED");
            notification.setErrorMessage(e.getMessage());
        }

        notification = notificationRepository.save(notification);
        return toDto(notification);
    }

    @Transactional(readOnly = true)
    public NotificationDto getNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new com.squadron.common.exception.ResourceNotFoundException(
                        "Notification", id));
        return toDto(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listUnread(UUID userId) {
        return notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "SENT")
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndStatus(userId, "SENT");
    }

    public NotificationDto markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.squadron.common.exception.ResourceNotFoundException(
                        "Notification", notificationId));

        notification.setStatus("READ");
        notification.setReadAt(Instant.now());
        notification = notificationRepository.save(notification);
        return toDto(notification);
    }

    public void markAllRead(UUID userId) {
        List<Notification> unread = notificationRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, "SENT");

        Instant now = Instant.now();
        for (Notification notification : unread) {
            notification.setStatus("READ");
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        log.info("Marked {} notifications as read for user {}", unread.size(), userId);
    }

    /**
     * Retries sending a previously failed notification.
     * Used by NotificationRetryService for scheduled retry processing.
     */
    public void retrySend(Notification notification) {
        NotificationChannel channel = channelRegistry.getChannel(notification.getChannel());
        if (channel == null) {
            throw new IllegalStateException("Unknown channel: " + notification.getChannel());
        }
        channel.send(notification, null);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .tenantId(notification.getTenantId())
                .userId(notification.getUserId())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .relatedTaskId(notification.getRelatedTaskId())
                .eventType(notification.getEventType())
                .errorMessage(notification.getErrorMessage())
                .createdAt(notification.getCreatedAt())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
