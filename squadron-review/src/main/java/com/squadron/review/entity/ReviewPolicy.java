package com.squadron.review.entity;

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
@Table(name = "review_policies")
public class ReviewPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "team_id")
    private UUID teamId;

    @Builder.Default
    @Column(name = "min_human_approvals", nullable = false)
    private Integer minHumanApprovals = 1;

    @Builder.Default
    @Column(name = "require_ai_review")
    private Boolean requireAiReview = true;

    @Builder.Default
    @Column(name = "self_review_allowed")
    private Boolean selfReviewAllowed = true;

    @Column(name = "auto_request_reviewers", columnDefinition = "jsonb")
    private String autoRequestReviewers;

    @Column(name = "review_checklist", columnDefinition = "jsonb")
    private String reviewChecklist;

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
