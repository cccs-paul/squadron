package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewResultDtoTest {

    @Test
    void should_buildWithAllFields() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        List<ReviewResultDto.ReviewFinding> findings = List.of(
                ReviewResultDto.ReviewFinding.builder()
                        .severity("CRITICAL")
                        .filePath("Service.java")
                        .lineNumber(42)
                        .issue("Null pointer dereference")
                        .suggestion("Add null check")
                        .category("bug")
                        .build(),
                ReviewResultDto.ReviewFinding.builder()
                        .severity("MINOR")
                        .filePath("Util.java")
                        .lineNumber(10)
                        .issue("Naming convention")
                        .suggestion("Use camelCase")
                        .category("style")
                        .build()
        );

        ReviewResultDto dto = ReviewResultDto.builder()
                .reviewId(reviewId)
                .taskId(taskId)
                .status("CHANGES_REQUESTED")
                .summary("Found critical issues")
                .criticalCount(1)
                .majorCount(0)
                .minorCount(1)
                .suggestionCount(0)
                .findings(findings)
                .build();

        assertEquals(reviewId, dto.getReviewId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("CHANGES_REQUESTED", dto.getStatus());
        assertEquals("Found critical issues", dto.getSummary());
        assertEquals(1, dto.getCriticalCount());
        assertEquals(0, dto.getMajorCount());
        assertEquals(1, dto.getMinorCount());
        assertEquals(0, dto.getSuggestionCount());
        assertEquals(2, dto.getFindings().size());
    }

    @Test
    void should_buildReviewFindingWithAllFields() {
        ReviewResultDto.ReviewFinding finding = ReviewResultDto.ReviewFinding.builder()
                .severity("MAJOR")
                .filePath("Controller.java")
                .lineNumber(99)
                .issue("SQL injection risk")
                .suggestion("Use parameterized query")
                .category("security")
                .build();

        assertEquals("MAJOR", finding.getSeverity());
        assertEquals("Controller.java", finding.getFilePath());
        assertEquals(99, finding.getLineNumber());
        assertEquals("SQL injection risk", finding.getIssue());
        assertEquals("Use parameterized query", finding.getSuggestion());
        assertEquals("security", finding.getCategory());
    }

    @Test
    void should_supportNoArgsConstructor() {
        ReviewResultDto dto = new ReviewResultDto();
        assertNull(dto.getReviewId());
        assertNull(dto.getTaskId());
        assertNull(dto.getStatus());
        assertNull(dto.getSummary());
        assertEquals(0, dto.getCriticalCount());
        assertNull(dto.getFindings());
    }

    @Test
    void should_supportSetters() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ReviewResultDto dto = new ReviewResultDto();
        dto.setReviewId(reviewId);
        dto.setTaskId(taskId);
        dto.setStatus("APPROVED");
        dto.setSummary("All good");
        dto.setCriticalCount(0);
        dto.setMajorCount(0);
        dto.setMinorCount(2);
        dto.setSuggestionCount(3);
        dto.setFindings(List.of());

        assertEquals(reviewId, dto.getReviewId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals("All good", dto.getSummary());
        assertEquals(0, dto.getCriticalCount());
        assertEquals(0, dto.getMajorCount());
        assertEquals(2, dto.getMinorCount());
        assertEquals(3, dto.getSuggestionCount());
        assertTrue(dto.getFindings().isEmpty());
    }

    @Test
    void should_supportAllArgsConstructor() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ReviewResultDto dto = new ReviewResultDto(
                reviewId, taskId, "APPROVED", "LGTM",
                0, 0, 1, 2, List.of());

        assertEquals(reviewId, dto.getReviewId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals("LGTM", dto.getSummary());
        assertEquals(0, dto.getCriticalCount());
        assertEquals(0, dto.getMajorCount());
        assertEquals(1, dto.getMinorCount());
        assertEquals(2, dto.getSuggestionCount());
        assertTrue(dto.getFindings().isEmpty());
    }
}
