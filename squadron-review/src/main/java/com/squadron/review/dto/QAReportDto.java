package com.squadron.review.dto;

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
public class QAReportDto {

    private UUID id;
    private UUID tenantId;
    private UUID taskId;
    private String verdict;
    private String summary;
    private Double lineCoverage;
    private Double branchCoverage;
    private Integer testsPassed;
    private Integer testsFailed;
    private Integer testsSkipped;
    private String findings;
    private String testGaps;
    private String coverageDetails;
    private Instant createdAt;
}
