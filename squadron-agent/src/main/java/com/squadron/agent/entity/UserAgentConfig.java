package com.squadron.agent.entity;

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
 * Represents an individual AI agent in a user's personal squadron.
 * Each user has a configurable set of agents (default 8, min 1, max configurable).
 * Agent names must be unique per user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_agent_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_agent_name", columnNames = {"tenant_id", "user_id", "agent_name"})
})
public class UserAgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "model", length = 200)
    private String model;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "system_prompt_override", columnDefinition = "TEXT")
    private String systemPromptOverride;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
