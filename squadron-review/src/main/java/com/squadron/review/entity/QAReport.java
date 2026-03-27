package com.squadron.review.entity;

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
@Table(name = "qa_reports")
public class QAReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String verdict;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "line_coverage")
    private Double lineCoverage;

    @Column(name = "branch_coverage")
    private Double branchCoverage;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_failed")
    private Integer testsFailed;

    @Column(name = "tests_skipped")
    private Integer testsSkipped;

    @Column(columnDefinition = "jsonb")
    private String findings;

    @Column(name = "test_gaps", columnDefinition = "jsonb")
    private String testGaps;

    @Column(name = "coverage_details", columnDefinition = "jsonb")
    private String coverageDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
