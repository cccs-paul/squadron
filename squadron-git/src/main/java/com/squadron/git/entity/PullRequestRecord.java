package com.squadron.git.entity;

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
@Table(name = "pull_request_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "external_pr_id", nullable = false, length = 255)
    private String externalPrId;

    @Column(name = "external_pr_url", length = 1024)
    private String externalPrUrl;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(name = "source_branch", nullable = false, length = 255)
    private String sourceBranch;

    @Column(name = "target_branch", nullable = false, length = 255)
    private String targetBranch;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

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
