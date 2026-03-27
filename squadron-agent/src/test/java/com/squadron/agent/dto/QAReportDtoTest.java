package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QAReportDtoTest {

    @Test
    void should_buildWithAllFields() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        List<QAReportDto.QAFinding> findings = List.of(
                QAReportDto.QAFinding.builder()
                        .category("REQUIREMENTS_COVERAGE")
                        .status("PASS")
                        .description("All requirements are covered")
                        .recommendation("None needed")
                        .build(),
                QAReportDto.QAFinding.builder()
                        .category("EDGE_CASE")
                        .status("WARNING")
                        .description("Null input not tested")
                        .recommendation("Add null input test case")
                        .build()
        );

        List<String> testGaps = List.of(
                "Missing test for concurrent access",
                "No integration test for DB failure scenario"
        );

        QAReportDto dto = QAReportDto.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .verdict("CONDITIONAL_PASS")
                .summary("Tests pass but coverage gaps exist")
                .lineCoverage(78.5)
                .branchCoverage(62.3)
                .testsPassed(42)
                .testsFailed(0)
                .testsSkipped(3)
                .findings(findings)
                .testGaps(testGaps)
                .createdAt(now)
                .build();

        assertEquals(taskId, dto.getTaskId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("CONDITIONAL_PASS", dto.getVerdict());
        assertEquals("Tests pass but coverage gaps exist", dto.getSummary());
        assertEquals(78.5, dto.getLineCoverage(), 0.01);
        assertEquals(62.3, dto.getBranchCoverage(), 0.01);
        assertEquals(42, dto.getTestsPassed());
        assertEquals(0, dto.getTestsFailed());
        assertEquals(3, dto.getTestsSkipped());
        assertEquals(2, dto.getFindings().size());
        assertEquals(2, dto.getTestGaps().size());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_buildQAFindingWithAllFields() {
        QAReportDto.QAFinding finding = QAReportDto.QAFinding.builder()
                .category("REGRESSION_RISK")
                .status("FAIL")
                .description("Regression detected in login flow")
                .recommendation("Fix login validation logic")
                .build();

        assertEquals("REGRESSION_RISK", finding.getCategory());
        assertEquals("FAIL", finding.getStatus());
        assertEquals("Regression detected in login flow", finding.getDescription());
        assertEquals("Fix login validation logic", finding.getRecommendation());
    }

    @Test
    void should_supportNoArgsConstructor() {
        QAReportDto dto = new QAReportDto();
        assertNull(dto.getTaskId());
        assertNull(dto.getTenantId());
        assertNull(dto.getVerdict());
        assertNull(dto.getSummary());
        assertEquals(0.0, dto.getLineCoverage(), 0.01);
        assertEquals(0.0, dto.getBranchCoverage(), 0.01);
        assertEquals(0, dto.getTestsPassed());
        assertEquals(0, dto.getTestsFailed());
        assertEquals(0, dto.getTestsSkipped());
        assertNull(dto.getFindings());
        assertNull(dto.getTestGaps());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_supportSetters() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReportDto dto = new QAReportDto();
        dto.setTaskId(taskId);
        dto.setTenantId(tenantId);
        dto.setVerdict("PASS");
        dto.setSummary("All good");
        dto.setLineCoverage(95.0);
        dto.setBranchCoverage(88.0);
        dto.setTestsPassed(100);
        dto.setTestsFailed(0);
        dto.setTestsSkipped(1);
        dto.setFindings(List.of());
        dto.setTestGaps(List.of());
        dto.setCreatedAt(now);

        assertEquals(taskId, dto.getTaskId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("PASS", dto.getVerdict());
        assertEquals("All good", dto.getSummary());
        assertEquals(95.0, dto.getLineCoverage(), 0.01);
        assertEquals(88.0, dto.getBranchCoverage(), 0.01);
        assertEquals(100, dto.getTestsPassed());
        assertEquals(0, dto.getTestsFailed());
        assertEquals(1, dto.getTestsSkipped());
        assertTrue(dto.getFindings().isEmpty());
        assertTrue(dto.getTestGaps().isEmpty());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_supportAllArgsConstructor() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReportDto dto = new QAReportDto(
                taskId, tenantId, "FAIL", "Tests failing",
                50.0, 30.0, 10, 5, 2,
                List.of(), List.of("Missing edge case tests"), now);

        assertEquals(taskId, dto.getTaskId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals("FAIL", dto.getVerdict());
        assertEquals("Tests failing", dto.getSummary());
        assertEquals(50.0, dto.getLineCoverage(), 0.01);
        assertEquals(30.0, dto.getBranchCoverage(), 0.01);
        assertEquals(10, dto.getTestsPassed());
        assertEquals(5, dto.getTestsFailed());
        assertEquals(2, dto.getTestsSkipped());
        assertTrue(dto.getFindings().isEmpty());
        assertEquals(1, dto.getTestGaps().size());
        assertEquals(now, dto.getCreatedAt());
    }
}
