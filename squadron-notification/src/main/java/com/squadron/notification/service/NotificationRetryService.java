package com.squadron.notification.service;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MINUTES = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /**
     * Periodically retries failed notifications up to MAX_RETRIES times.
     * Runs every 5 minutes by default.
     */
    @Scheduled(fixedDelayString = "${squadron.notification.retry.interval-ms:300000}")
    @Transactional
    public void retryFailedNotifications() {
        Instant cutoff = Instant.now().minus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES);
        List<Notification> failed = notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                "FAILED", MAX_RETRIES, cutoff);

        if (failed.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed notifications", failed.size());

        for (Notification notification : failed) {
            try {
                notificationService.retrySend(notification);
                notification.setStatus("SENT");
                log.debug("Retry succeeded for notification {}", notification.getId());
            } catch (Exception e) {
                notification.setRetryCount(notification.getRetryCount() + 1);
                if (notification.getRetryCount() >= MAX_RETRIES) {
                    notification.setStatus("DEAD_LETTER");
                    log.warn("Notification {} moved to dead letter after {} retries",
                            notification.getId(), notification.getRetryCount());
                }
                log.debug("Retry failed for notification {}: {}", notification.getId(), e.getMessage());
            }
            notificationRepository.save(notification);
        }
    }

    /**
     * Gets dead letter notifications for manual review.
     */
    @Transactional(readOnly = true)
    public List<Notification> getDeadLetterNotifications(int limit) {
        return notificationRepository.findByStatusOrderByCreatedAtDesc("DEAD_LETTER")
                .stream()
                .limit(limit)
                .toList();
    }
}
