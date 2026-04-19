package com.squadron.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user configuration for the test data generator model.
 * Controls which LLM is used to generate fake plans, code, and reviews
 * when testing agents from the "My Agent Squadron" UI.
 */
@Entity
@Table(name = "agent_test_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "user_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTestConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Column(name = "generator_provider", nullable = false, length = 100)
    private String generatorProvider = "ollama";

    @Builder.Default
    @Column(name = "generator_model", nullable = false, length = 200)
    private String generatorModel = "gemma4:e2b";

    @Builder.Default
    @Column(name = "generator_hosting_type", nullable = false, length = 50)
    private String generatorHostingType = "SELF_HOSTED";

    @Column(name = "generator_base_url", length = 500)
    private String generatorBaseUrl;

    @Column(name = "generator_api_key", columnDefinition = "TEXT")
    private String generatorApiKey;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
