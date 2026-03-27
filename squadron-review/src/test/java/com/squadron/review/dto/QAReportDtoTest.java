package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QAReportDtoTest {

    @Test
    void should_buildWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReportDto dto = QAReportDto.builder()
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

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("PASS", dto.getVerdict());
        assertEquals("All tests pass", dto.getSummary());
        assertEquals(85.0, dto.getLineCoverage());
        assertEquals(75.0, dto.getBranchCoverage());
        assertEquals(100, dto.getTestsPassed());
        assertEquals(0, dto.getTestsFailed());
        assertEquals(5, dto.getTestsSkipped());
        assertEquals("{\"issues\": []}", dto.getFindings());
        assertEquals("{\"gaps\": []}", dto.getTestGaps());
        assertEquals("{\"details\": {}}", dto.getCoverageDetails());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_haveNoArgsConstructor() {
        QAReportDto dto = new QAReportDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getVerdict());
        assertNull(dto.getSummary());
        assertNull(dto.getLineCoverage());
    }

    @Test
    void should_setAndGetFields() {
        QAReportDto dto = new QAReportDto();

        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        dto.setId(id);
        dto.setTenantId(tenantId);
        dto.setTaskId(taskId);
        dto.setVerdict("FAIL");
        dto.setSummary("Tests failed");
        dto.setLineCoverage(50.0);
        dto.setBranchCoverage(40.0);
        dto.setTestsPassed(80);
        dto.setTestsFailed(20);
        dto.setTestsSkipped(10);
        dto.setFindings("{\"issues\": [\"npe\"]}");
        dto.setTestGaps("{\"gaps\": [\"missing\"]}");
        dto.setCoverageDetails("{\"details\": {\"low\": true}}");
        dto.setCreatedAt(now);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("FAIL", dto.getVerdict());
        assertEquals("Tests failed", dto.getSummary());
        assertEquals(50.0, dto.getLineCoverage());
        assertEquals(40.0, dto.getBranchCoverage());
        assertEquals(80, dto.getTestsPassed());
        assertEquals(20, dto.getTestsFailed());
        assertEquals(10, dto.getTestsSkipped());
        assertEquals("{\"issues\": [\"npe\"]}", dto.getFindings());
        assertEquals("{\"gaps\": [\"missing\"]}", dto.getTestGaps());
        assertEquals("{\"details\": {\"low\": true}}", dto.getCoverageDetails());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_haveEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        QAReportDto dto1 = QAReportDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .build();

        QAReportDto dto2 = QAReportDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .build();

        QAReportDto dto3 = QAReportDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("FAIL")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertNotEquals(dto1, dto3);
        assertNotNull(dto1.toString());
    }
}
