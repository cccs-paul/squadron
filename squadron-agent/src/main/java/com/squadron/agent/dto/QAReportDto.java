package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a QA agent report for a task. Contains the overall verdict,
 * coverage metrics, test results, identified findings, and test gaps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QAReportDto {

    private UUID taskId;
    private UUID tenantId;

    /** Overall QA verdict: PASS, CONDITIONAL_PASS, or FAIL */
    private String verdict;

    /** Human-readable summary of QA findings */
    private String summary;

    /** Line coverage percentage (0-100) */
    private double lineCoverage;

    /** Branch coverage percentage (0-100) */
    private double branchCoverage;

    /** Number of tests that passed */
    private int testsPassed;

    /** Number of tests that failed */
    private int testsFailed;

    /** Number of tests that were skipped */
    private int testsSkipped;

    /** Detailed QA findings by category */
    private List<QAFinding> findings;

    /** Identified gaps in test coverage */
    private List<String> testGaps;

    /** Timestamp when the report was created */
    private Instant createdAt;

    /**
     * Represents a single QA finding with category, status, description,
     * and recommendation for resolution.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QAFinding {

        /**
         * Finding category: REQUIREMENTS_COVERAGE, EDGE_CASE, REGRESSION_RISK,
         * DATA_INTEGRITY, ERROR_SCENARIO, INTEGRATION
         */
        private String category;

        /** Finding status: PASS, FAIL, or WARNING */
        private String status;

        /** Description of what was found */
        private String description;

        /** Recommended action to address the finding */
        private String recommendation;
    }
}
