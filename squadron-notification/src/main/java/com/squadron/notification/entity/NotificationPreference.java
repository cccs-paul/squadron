package com.squadron.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enable_email", nullable = false)
    @Builder.Default
    private Boolean enableEmail = true;

    @Column(name = "enable_slack", nullable = false)
    @Builder.Default
    private Boolean enableSlack = false;

    @Column(name = "enable_teams", nullable = false)
    @Builder.Default
    private Boolean enableTeams = false;

    @Column(name = "enable_in_app", nullable = false)
    @Builder.Default
    private Boolean enableInApp = true;

    @Column(name = "slack_webhook_url", length = 1024)
    private String slackWebhookUrl;

    @Column(name = "teams_webhook_url", length = 1024)
    private String teamsWebhookUrl;

    @Column(name = "email_address", length = 255)
    private String emailAddress;

    @Column(name = "muted_event_types", columnDefinition = "jsonb")
    private String mutedEventTypes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
