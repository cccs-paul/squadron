package com.squadron.notification.repository;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.notification.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.notification.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_notification_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Notification createNotification(UUID tenantId, UUID userId, String channel, String status) {
        Notification notification = Notification.builder()
                .tenantId(tenantId)
                .userId(userId)
                .channel(channel)
                .subject("Test notification")
                .body("This is a test notification body")
                .status(status)
                .eventType("TASK_COMPLETED")
                .retryCount(0)
                .build();
        return entityManager.persistFlushFind(notification);
    }

    private Notification createNotification(UUID tenantId, UUID userId, String status) {
        return createNotification(tenantId, userId, "EMAIL", status);
    }

    private NotificationPreference createPreference(UUID userId, UUID tenantId) {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .enableInApp(true)
                .emailAddress(userId + "@example.com")
                .build();
        return entityManager.persistFlushFind(pref);
    }

    // =========================================================================
    // NotificationRepository Tests
    // =========================================================================

    @Test
    void should_saveNotification_when_validEntity() {
        Notification notification = Notification.builder()
                .tenantId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .channel("EMAIL")
                .subject("Build completed")
                .body("Your build has completed successfully")
                .eventType("BUILD_COMPLETED")
                .relatedTaskId(UUID.randomUUID())
                .build();

        Notification saved = notificationRepository.save(notification);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getChannel()).isEqualTo("EMAIL");
        assertThat(saved.getSubject()).isEqualTo("Build completed");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_saveNotification_when_userIdIsNull() {
        Notification notification = Notification.builder()
                .tenantId(UUID.randomUUID())
                .channel("WEBHOOK")
                .subject("System event")
                .body("System-wide notification without specific user")
                .build();

        Notification saved = notificationRepository.save(notification);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isNull();
    }

    @Test
    void should_findById_when_notificationExists() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = createNotification(tenantId, userId, "PENDING");

        Optional<Notification> found = notificationRepository.findById(notification.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSubject()).isEqualTo("Test notification");
    }

    @Test
    void should_findByUserIdAndStatusOrderByCreatedAtDesc_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createNotification(tenantId, userId, "PENDING");
        createNotification(tenantId, userId, "PENDING");
        createNotification(tenantId, userId, "SENT");
        createNotification(tenantId, UUID.randomUUID(), "PENDING");

        List<Notification> pendingNotifications =
                notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");

        assertThat(pendingNotifications).hasSize(2);
        assertThat(pendingNotifications).allMatch(n -> n.getUserId().equals(userId) && "PENDING".equals(n.getStatus()));
    }

    @Test
    void should_findByUserIdOrderByCreatedAtDesc_when_notificationsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createNotification(tenantId, userId, "PENDING");
        createNotification(tenantId, userId, "SENT");
        createNotification(tenantId, userId, "FAILED");

        List<Notification> results = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(n -> n.getUserId().equals(userId));
    }

    @Test
    void should_countByUserIdAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createNotification(tenantId, userId, "PENDING");
        createNotification(tenantId, userId, "PENDING");
        createNotification(tenantId, userId, "SENT");

        long pendingCount = notificationRepository.countByUserIdAndStatus(userId, "PENDING");
        long sentCount = notificationRepository.countByUserIdAndStatus(userId, "SENT");

        assertThat(pendingCount).isEqualTo(2);
        assertThat(sentCount).isEqualTo(1);
    }

    @Test
    void should_findByStatus_when_notificationsExist() {
        UUID tenantId = UUID.randomUUID();
        createNotification(tenantId, UUID.randomUUID(), "FAILED");
        createNotification(tenantId, UUID.randomUUID(), "FAILED");
        createNotification(tenantId, UUID.randomUUID(), "SENT");

        List<Notification> failedNotifications = notificationRepository.findByStatus("FAILED");

        assertThat(failedNotifications).hasSizeGreaterThanOrEqualTo(2);
        assertThat(failedNotifications).allMatch(n -> "FAILED".equals(n.getStatus()));
    }

    @Test
    void should_findByStatusAndRetryCountLessThanAndCreatedAtAfter_when_matching() {
        UUID tenantId = UUID.randomUUID();
        Notification n1 = createNotification(tenantId, UUID.randomUUID(), "FAILED");
        n1.setRetryCount(1);
        entityManager.persistAndFlush(n1);

        Notification n2 = createNotification(tenantId, UUID.randomUUID(), "FAILED");
        n2.setRetryCount(5);
        entityManager.persistAndFlush(n2);

        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);

        List<Notification> results =
                notificationRepository.findByStatusAndRetryCountLessThanAndCreatedAtAfter(
                        "FAILED", 3, threshold);

        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        assertThat(results).allMatch(n -> n.getRetryCount() < 3 && "FAILED".equals(n.getStatus()));
    }

    @Test
    void should_findByStatusOrderByCreatedAtDesc_when_matching() {
        UUID tenantId = UUID.randomUUID();
        createNotification(tenantId, UUID.randomUUID(), "PENDING");
        createNotification(tenantId, UUID.randomUUID(), "PENDING");

        List<Notification> results =
                notificationRepository.findByStatusOrderByCreatedAtDesc("PENDING");

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(n -> "PENDING".equals(n.getStatus()));
    }

    @Test
    void should_deleteNotification_when_exists() {
        UUID tenantId = UUID.randomUUID();
        Notification notification = createNotification(tenantId, UUID.randomUUID(), "PENDING");
        UUID notifId = notification.getId();

        notificationRepository.deleteById(notifId);
        entityManager.flush();

        assertThat(notificationRepository.findById(notifId)).isEmpty();
    }

    @Test
    void should_updateNotification_when_statusChanged() {
        UUID tenantId = UUID.randomUUID();
        Notification notification = createNotification(tenantId, UUID.randomUUID(), "PENDING");

        notification.setStatus("SENT");
        notification.setSentAt(Instant.now());

        notificationRepository.save(notification);
        entityManager.flush();
        entityManager.clear();

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("SENT");
        assertThat(updated.getSentAt()).isNotNull();
    }

    // =========================================================================
    // NotificationPreferenceRepository Tests
    // =========================================================================

    @Test
    void should_saveNotificationPreference_when_validEntity() {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .enableEmail(true)
                .enableSlack(true)
                .enableTeams(false)
                .enableInApp(true)
                .slackWebhookUrl("https://hooks.slack.com/services/xxx")
                .emailAddress("user@example.com")
                .mutedEventTypes("[\"BUILD_STARTED\"]")
                .build();

        NotificationPreference saved = notificationPreferenceRepository.save(pref);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEnableEmail()).isTrue();
        assertThat(saved.getEnableSlack()).isTrue();
        assertThat(saved.getEnableTeams()).isFalse();
        assertThat(saved.getEnableInApp()).isTrue();
        assertThat(saved.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/xxx");
        assertThat(saved.getMutedEventTypes()).contains("BUILD_STARTED");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findByUserId_when_preferenceExists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        createPreference(userId, tenantId);

        Optional<NotificationPreference> found =
                notificationPreferenceRepository.findByUserId(userId);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getEnableEmail()).isTrue();
    }

    @Test
    void should_returnEmpty_when_preferenceNotFoundForUser() {
        Optional<NotificationPreference> found =
                notificationPreferenceRepository.findByUserId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_deletePreference_when_exists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        NotificationPreference pref = createPreference(userId, tenantId);
        UUID prefId = pref.getId();

        notificationPreferenceRepository.deleteById(prefId);
        entityManager.flush();

        assertThat(notificationPreferenceRepository.findById(prefId)).isEmpty();
    }

    @Test
    void should_updatePreference_when_fieldsChanged() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        NotificationPreference pref = createPreference(userId, tenantId);

        pref.setEnableSlack(true);
        pref.setSlackWebhookUrl("https://hooks.slack.com/services/yyy");

        notificationPreferenceRepository.save(pref);
        entityManager.flush();
        entityManager.clear();

        NotificationPreference updated =
                notificationPreferenceRepository.findById(pref.getId()).orElseThrow();
        assertThat(updated.getEnableSlack()).isTrue();
        assertThat(updated.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/yyy");
    }
}
