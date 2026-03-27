package com.squadron.review.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QAReportTest {

    @Test
    void should_buildWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReport report = QAReport.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All tests pass")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .findings("{\"issues\": []}")
                .testGaps("{\"gaps\": []}")
                .coverageDetails("{\"details\": {}}")
                .createdAt(now)
                .build();

        assertEquals(id, report.getId());
        assertEquals(tenantId, report.getTenantId());
        assertEquals(taskId, report.getTaskId());
        assertEquals("PASS", report.getVerdict());
        assertEquals("All tests pass", report.getSummary());
        assertEquals(85.0, report.getLineCoverage());
        assertEquals(75.0, report.getBranchCoverage());
        assertEquals(100, report.getTestsPassed());
        assertEquals(0, report.getTestsFailed());
        assertEquals(5, report.getTestsSkipped());
        assertEquals("{\"issues\": []}", report.getFindings());
        assertEquals("{\"gaps\": []}", report.getTestGaps());
        assertEquals("{\"details\": {}}", report.getCoverageDetails());
        assertEquals(now, report.getCreatedAt());
    }

    @Test
    void should_haveDefaultValues() {
        QAReport report = new QAReport();

        assertNull(report.getId());
        assertNull(report.getTenantId());
        assertNull(report.getTaskId());
        assertNull(report.getVerdict());
        assertNull(report.getSummary());
        assertNull(report.getLineCoverage());
        assertNull(report.getBranchCoverage());
        assertNull(report.getTestsPassed());
        assertNull(report.getTestsFailed());
        assertNull(report.getTestsSkipped());
        assertNull(report.getFindings());
        assertNull(report.getTestGaps());
        assertNull(report.getCoverageDetails());
        assertNull(report.getCreatedAt());
    }

    @Test
    void should_setFieldsViaSetters() {
        QAReport report = new QAReport();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        report.setId(id);
        report.setTenantId(tenantId);
        report.setTaskId(taskId);
        report.setVerdict("FAIL");
        report.setSummary("Tests failed");
        report.setLineCoverage(50.0);
        report.setBranchCoverage(40.0);
        report.setTestsPassed(80);
        report.setTestsFailed(20);
        report.setTestsSkipped(10);
        report.setFindings("{\"issues\": [\"npe\"]}");
        report.setTestGaps("{\"gaps\": [\"missing\"]}");
        report.setCoverageDetails("{\"details\": {\"low\": true}}");

        assertEquals(id, report.getId());
        assertEquals(tenantId, report.getTenantId());
        assertEquals(taskId, report.getTaskId());
        assertEquals("FAIL", report.getVerdict());
        assertEquals("Tests failed", report.getSummary());
        assertEquals(50.0, report.getLineCoverage());
        assertEquals(40.0, report.getBranchCoverage());
        assertEquals(80, report.getTestsPassed());
        assertEquals(20, report.getTestsFailed());
        assertEquals(10, report.getTestsSkipped());
        assertEquals("{\"issues\": [\"npe\"]}", report.getFindings());
        assertEquals("{\"gaps\": [\"missing\"]}", report.getTestGaps());
        assertEquals("{\"details\": {\"low\": true}}", report.getCoverageDetails());
    }

    @Test
    void should_setCreatedAtOnPrePersist() {
        QAReport report = QAReport.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .verdict("PASS")
                .build();

        assertNull(report.getCreatedAt());

        report.onCreate();

        assertNotNull(report.getCreatedAt());
    }

    @Test
    void should_haveAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReport report = new QAReport(id, tenantId, taskId, "PASS", "Summary",
                85.0, 75.0, 100, 0, 5,
                "{}", "{}", "{}", now);

        assertEquals(id, report.getId());
        assertEquals(tenantId, report.getTenantId());
        assertEquals(taskId, report.getTaskId());
        assertEquals("PASS", report.getVerdict());
        assertEquals("Summary", report.getSummary());
        assertEquals(85.0, report.getLineCoverage());
        assertEquals(75.0, report.getBranchCoverage());
        assertEquals(100, report.getTestsPassed());
        assertEquals(0, report.getTestsFailed());
        assertEquals(5, report.getTestsSkipped());
        assertEquals("{}", report.getFindings());
        assertEquals("{}", report.getTestGaps());
        assertEquals("{}", report.getCoverageDetails());
        assertEquals(now, report.getCreatedAt());
    }

    @Test
    void should_haveEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReport report1 = QAReport.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .createdAt(now)
                .build();

        QAReport report2 = QAReport.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .createdAt(now)
                .build();

        QAReport report3 = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("FAIL")
                .createdAt(now)
                .build();

        assertEquals(report1, report2);
        assertEquals(report1.hashCode(), report2.hashCode());
        assertNotEquals(report1, report3);
    }
}
