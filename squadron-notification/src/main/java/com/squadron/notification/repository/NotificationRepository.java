package com.squadron.notification.repository;

import com.squadron.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    List<Notification> findByStatus(String status);

    List<Notification> findByStatusAndRetryCountLessThanAndCreatedAtAfter(String status, int maxRetries, Instant after);

    List<Notification> findByStatusOrderByCreatedAtDesc(String status);
}
