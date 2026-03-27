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
@Table(name = "user_platform_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    /**
     * Encrypted access token. Values are encrypted using AES-256-GCM via TokenEncryptionService
     * before storage and must be decrypted before use.
     */
    @Column(name = "access_token", nullable = false, length = 2048)
    private String accessToken;

    /**
     * Encrypted refresh token. Values are encrypted using AES-256-GCM via TokenEncryptionService
     * before storage and must be decrypted before use. Null for PAT-type tokens.
     */
    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(length = 1024)
    private String scopes;

    /**
     * Token type: "oauth2" for OAuth2 tokens or "pat" for personal access tokens.
     */
    @Column(name = "token_type", length = 50)
    @Builder.Default
    private String tokenType = "oauth2";

    /**
     * Extra OAuth2 token metadata stored as JSONB (e.g., token_type from OAuth response, scope details).
     */
    @Column(name = "token_metadata", columnDefinition = "jsonb")
    private String tokenMetadata;

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
