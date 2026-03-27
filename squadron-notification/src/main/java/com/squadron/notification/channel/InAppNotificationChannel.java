package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationChannel implements NotificationChannel {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public String getChannelType() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification, NotificationPreference preference) {
        log.info("Sending in-app notification {} to user {}", notification.getId(), notification.getUserId());

        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());

        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + notification.getUserId(),
                    notification
            );
            log.debug("WebSocket push sent for notification {}", notification.getId());
        } catch (Exception e) {
            log.warn("Failed to push notification {} via WebSocket, but it is stored in DB: {}",
                    notification.getId(), e.getMessage());
        }
    }
}
