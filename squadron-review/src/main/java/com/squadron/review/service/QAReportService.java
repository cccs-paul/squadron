package com.squadron.review.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.QAReportDto;
import com.squadron.review.entity.QAReport;
import com.squadron.review.repository.QAReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class QAReportService {

    private static final Logger log = LoggerFactory.getLogger(QAReportService.class);

    private final QAReportRepository qaReportRepository;
    private final NatsEventPublisher natsEventPublisher;

    public QAReportService(QAReportRepository qaReportRepository,
                           NatsEventPublisher natsEventPublisher) {
        this.qaReportRepository = qaReportRepository;
        this.natsEventPublisher = natsEventPublisher;
    }

    public QAReport createReport(QAReportDto dto) {
        log.info("Creating QA report for task {} with verdict {}", dto.getTaskId(), dto.getVerdict());

        QAReport report = QAReport.builder()
                .tenantId(dto.getTenantId())
                .taskId(dto.getTaskId())
                .verdict(dto.getVerdict())
                .summary(dto.getSummary())
                .lineCoverage(dto.getLineCoverage())
                .branchCoverage(dto.getBranchCoverage())
                .testsPassed(dto.getTestsPassed())
                .testsFailed(dto.getTestsFailed())
                .testsSkipped(dto.getTestsSkipped())
                .findings(dto.getFindings())
                .testGaps(dto.getTestGaps())
                .coverageDetails(dto.getCoverageDetails())
                .build();

        QAReport saved = qaReportRepository.save(report);

        // Publish QA report created event
        SquadronEvent event = new SquadronEvent();
        event.setEventType("QA_REPORT_CREATED");
        event.setTenantId(dto.getTenantId());
        event.setSource("squadron-review");
        natsEventPublisher.publish("squadron.qa.report.created", event);

        log.info("Created QA report {} for task {}", saved.getId(), saved.getTaskId());
        return saved;
    }

    @Transactional(readOnly = true)
    public QAReport getLatestReport(UUID taskId) {
        return qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("QAReport", taskId));
    }

    @Transactional(readOnly = true)
    public List<QAReport> getReportsForTask(UUID taskId) {
        return qaReportRepository.findByTaskId(taskId);
    }

    public boolean checkQAGate(UUID taskId) {
        QAReport latest = getLatestReport(taskId);
        return "PASS".equals(latest.getVerdict()) || "CONDITIONAL_PASS".equals(latest.getVerdict());
    }

    public QAReportDto toDto(QAReport report) {
        return QAReportDto.builder()
                .id(report.getId())
                .tenantId(report.getTenantId())
                .taskId(report.getTaskId())
                .verdict(report.getVerdict())
                .summary(report.getSummary())
                .lineCoverage(report.getLineCoverage())
                .branchCoverage(report.getBranchCoverage())
                .testsPassed(report.getTestsPassed())
                .testsFailed(report.getTestsFailed())
                .testsSkipped(report.getTestsSkipped())
                .findings(report.getFindings())
                .testGaps(report.getTestGaps())
                .coverageDetails(report.getCoverageDetails())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
