package com.squadron.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "token_usages")
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Builder.Default
    @Column(name = "input_tokens", nullable = false)
    private long inputTokens = 0;

    @Builder.Default
    @Column(name = "output_tokens", nullable = false)
    private long outputTokens = 0;

    @Builder.Default
    @Column(name = "total_tokens", nullable = false)
    private long totalTokens = 0;

    @Builder.Default
    @Column(name = "estimated_cost")
    private double estimatedCost = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
