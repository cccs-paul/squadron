package com.squadron.git.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_strategies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "strategy_type", nullable = false, length = 50)
    private String strategyType;

    @Column(name = "branch_prefix", length = 100)
    @Builder.Default
    private String branchPrefix = "squadron/";

    @Column(name = "target_branch", nullable = false, length = 100)
    @Builder.Default
    private String targetBranch = "main";

    @Column(name = "development_branch", length = 100)
    private String developmentBranch;

    @Column(name = "branch_name_template", length = 255)
    @Builder.Default
    private String branchNameTemplate = "{prefix}{taskId}/{slug}";

    @Column(name = "merge_method", length = 20)
    @Builder.Default
    private String mergeMethod = "MERGE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
