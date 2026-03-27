package com.squadron.review.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.QAReportDto;
import com.squadron.review.entity.QAReport;
import com.squadron.review.repository.QAReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QAReportServiceTest {

    @Mock
    private QAReportRepository qaReportRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private QAReportService qaReportService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        qaReportService = new QAReportService(qaReportRepository, natsEventPublisher);
    }

    @Test
    void should_createReport() {
        QAReportDto dto = QAReportDto.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All tests pass")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .build();

        QAReport savedReport = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All tests pass")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.save(any(QAReport.class))).thenReturn(savedReport);
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        QAReport result = qaReportService.createReport(dto);

        assertNotNull(result);
        assertEquals("PASS", result.getVerdict());
        assertEquals(taskId, result.getTaskId());
        verify(qaReportRepository).save(any(QAReport.class));
        verify(natsEventPublisher).publish(anyString(), any());
    }

    @Test
    void should_getLatestReport() {
        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All good")
                .lineCoverage(85.0)
                .branchCoverage(75.0)
                .testsPassed(100)
                .testsFailed(0)
                .testsSkipped(5)
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.of(report));

        QAReport result = qaReportService.getLatestReport(taskId);

        assertNotNull(result);
        assertEquals("PASS", result.getVerdict());
        assertEquals(taskId, result.getTaskId());
    }

    @Test
    void should_getLatestReport_throwsNotFound() {
        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> qaReportService.getLatestReport(taskId));
    }

    @Test
    void should_getReportsForTask() {
        QAReport report1 = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .createdAt(Instant.now())
                .build();

        QAReport report2 = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("FAIL")
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.findByTaskId(taskId)).thenReturn(List.of(report1, report2));

        List<QAReport> results = qaReportService.getReportsForTask(taskId);

        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void should_checkQAGate_passVerdict() {
        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.of(report));

        boolean result = qaReportService.checkQAGate(taskId);

        assertTrue(result);
    }

    @Test
    void should_checkQAGate_conditionalPassVerdict() {
        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("CONDITIONAL_PASS")
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.of(report));

        boolean result = qaReportService.checkQAGate(taskId);

        assertTrue(result);
    }

    @Test
    void should_checkQAGate_failVerdict() {
        QAReport report = QAReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("FAIL")
                .createdAt(Instant.now())
                .build();

        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.of(report));

        boolean result = qaReportService.checkQAGate(taskId);

        assertFalse(result);
    }

    @Test
    void should_checkQAGate_throwsWhenNoReport() {
        when(qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> qaReportService.checkQAGate(taskId));
    }

    @Test
    void should_toDto_mapsAllFields() {
        UUID reportId = UUID.randomUUID();
        Instant now = Instant.now();

        QAReport report = QAReport.builder()
                .id(reportId)
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict("PASS")
                .summary("All good")
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

        QAReportDto dto = qaReportService.toDto(report);

        assertNotNull(dto);
        assertEquals(reportId, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("PASS", dto.getVerdict());
        assertEquals("All good", dto.getSummary());
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
}
