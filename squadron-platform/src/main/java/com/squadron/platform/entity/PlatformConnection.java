package com.squadron.platform.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_connections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "platform_type", nullable = false, length = 50)
    private String platformType;

    @Column(name = "base_url", nullable = false, length = 1024)
    private String baseUrl;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType;

    /**
     * Credentials stored as JSONB. Sensitive values within this JSON (e.g., clientSecret, accessToken, pat, apiKey)
     * are encrypted at rest using AES-256-GCM via TokenEncryptionService.
     * The JSON structure itself is not encrypted, but individual sensitive field values are.
     * Example: {"clientId": "plaintext", "clientSecret": "ENCRYPTED_VALUE", "tokenEndpoint": "plaintext"}
     */
    @Column(columnDefinition = "jsonb")
    private String credentials;

    /**
     * Webhook secret for signature verification. Stored encrypted using TokenEncryptionService.
     * If null, webhook signature verification is skipped for this connection.
     */
    @Column(name = "webhook_secret", length = 512)
    private String webhookSecret;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "jsonb")
    private String metadata;

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
