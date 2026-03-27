package com.squadron.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO capturing the outcome of an AI code review, including aggregate
 * finding counts and the detailed list of individual review findings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResultDto {

    private UUID reviewId;
    private UUID taskId;
    private String status;
    private String summary;
    private int criticalCount;
    private int majorCount;
    private int minorCount;
    private int suggestionCount;
    private List<ReviewFinding> findings;

    /**
     * An individual review finding produced by the AI review agent.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewFinding {
        private String severity;
        private String filePath;
        private Integer lineNumber;
        private String issue;
        private String suggestion;
        private String category;
    }
}
