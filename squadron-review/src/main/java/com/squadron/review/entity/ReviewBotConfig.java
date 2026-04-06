package com.squadron.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration for a review bot on a git platform connection.
 * When enabled, the AI review agent will post review comments as the bot account
 * on the git platform in addition to the internal review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "review_bot_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "connection_id"})
})
public class ReviewBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "bot_username", nullable = false, length = 255)
    private String botUsername;

    /**
     * Encrypted access token for the bot account.
     * Encrypted via TokenEncryptionService before storage.
     */
    @Column(name = "bot_access_token", nullable = false, length = 2048)
    private String botAccessToken;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * If true, the bot is automatically assigned as a reviewer on new PRs.
     */
    @Column(name = "auto_assign", nullable = false)
    @Builder.Default
    private boolean autoAssign = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
