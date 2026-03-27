package com.squadron.review.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.review.dto.QAReportDto;
import com.squadron.review.entity.QAReport;
import com.squadron.review.service.QAReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/qa-reports")
public class QAReportController {

    private final QAReportService qaReportService;

    public QAReportController(QAReportService qaReportService) {
        this.qaReportService = qaReportService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer', 'qa')")
    public ResponseEntity<ApiResponse<QAReport>> createReport(@Valid @RequestBody QAReportDto dto) {
        QAReport report = qaReportService.createReport(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(report));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer', 'qa', 'viewer')")
    public ResponseEntity<ApiResponse<List<QAReport>>> getReportsForTask(@PathVariable UUID taskId) {
        List<QAReport> reports = qaReportService.getReportsForTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/task/{taskId}/latest")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer', 'qa', 'viewer')")
    public ResponseEntity<ApiResponse<QAReport>> getLatestReport(@PathVariable UUID taskId) {
        QAReport report = qaReportService.getLatestReport(taskId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/task/{taskId}/gate")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer', 'qa', 'viewer')")
    public ResponseEntity<ApiResponse<Boolean>> checkQAGate(@PathVariable UUID taskId) {
        boolean gatePassed = qaReportService.checkQAGate(taskId);
        return ResponseEntity.ok(ApiResponse.success(gatePassed));
    }
}
