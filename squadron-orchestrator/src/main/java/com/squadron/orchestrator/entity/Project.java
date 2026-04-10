package com.squadron.orchestrator.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "external_project_id")
    private String externalProjectId;

    @JsonProperty("repositoryUrl")
    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "git_connection_id")
    private UUID gitConnectionId;

    @Column(name = "clone_url", length = 1000)
    private String cloneUrl;

    @Builder.Default
    @Column(name = "default_branch")
    private String defaultBranch = "main";

    @Builder.Default
    @Column(name = "branch_strategy")
    private String branchStrategy = "TRUNK_BASED";

    @Builder.Default
    @Column(name = "branch_naming_template", length = 500)
    private String branchNamingTemplate = "{strategy}/{ticket}-{description}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String settings;

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
