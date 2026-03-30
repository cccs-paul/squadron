package com.squadron.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for communicating with the squadron-review service.
 * Provides methods to create reviews, submit review findings, and
 * check review gate status for tasks.
 */
@Service
public class ReviewClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewClient.class);

    private final WebClient webClient;

    @Autowired
    public ReviewClient(@Value("${squadron.review.url:http://localhost:8088}") String reviewUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(reviewUrl)
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built WebClient.
     */
    ReviewClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Creates a new AI review for a task in the squadron-review service.
     *
     * @param tenantId     the tenant ID
     * @param taskId       the task being reviewed
     * @param reviewerType the type of reviewer (e.g. "AI")
     * @return the created review response
     */
    public ReviewResponse createReview(UUID tenantId, UUID taskId, String reviewerType) {
        log.debug("Creating review for tenant {} task {} type {}", tenantId, taskId, reviewerType);

        Map<String, Object> requestBody = Map.of(
                "tenantId", tenantId.toString(),
                "taskId", taskId.toString(),
                "reviewerType", reviewerType
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/reviews")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return extractReviewFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to create review: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ReviewClientException("Failed to create review: " + e.getStatusCode(), e);
        }
    }

    /**
     * Submits a completed review with status, summary, and inline comments.
     *
     * @param reviewId the review ID to submit
     * @param status   the review outcome (e.g. "APPROVED", "CHANGES_REQUESTED")
     * @param summary  a summary of the review findings
     * @param comments the list of inline review comments
     * @return the updated review response
     */
    public ReviewResponse submitReview(UUID reviewId, String status, String summary,
                                        List<ReviewCommentRequest> comments) {
        log.debug("Submitting review {} with status {}", reviewId, status);

        List<Map<String, Object>> commentMaps = comments.stream()
                .map(c -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("filePath", c.getFilePath());
                    map.put("lineNumber", c.getLineNumber());
                    map.put("body", c.getBody());
                    map.put("severity", c.getSeverity());
                    map.put("category", c.getCategory());
                    return map;
                })
                .toList();

        Map<String, Object> requestBody = Map.of(
                "reviewId", reviewId.toString(),
                "status", status,
                "summary", summary,
                "comments", commentMaps
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/reviews/submit")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return extractReviewFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to submit review: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ReviewClientException("Failed to submit review: " + e.getStatusCode(), e);
        }
    }

    /**
     * Checks the review gate status for a task. Returns whether the task
     * has met the required review policy (e.g. minimum human approvals, AI approval).
     *
     * @param taskId   the task ID
     * @param tenantId the tenant ID
     * @param teamId   the team ID (for team-level policy resolution)
     * @return the review gate response
     */
    public ReviewGateResponse checkReviewGate(UUID taskId, UUID tenantId, UUID teamId) {
        log.debug("Checking review gate for task {} tenant {} team {}", taskId, tenantId, teamId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/reviews/task/{taskId}/gate")
                            .queryParam("tenantId", tenantId.toString())
                            .queryParam("teamId", teamId.toString())
                            .build(taskId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return extractGateFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to check review gate: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ReviewClientException("Failed to check review gate: " + e.getStatusCode(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ReviewResponse extractReviewFromResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            throw new ReviewClientException("Empty response from review endpoint");
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return ReviewResponse.builder()
                .id(parseUuid(data, "id"))
                .taskId(parseUuid(data, "taskId"))
                .status(getStringValue(data, "status"))
                .summary(getStringValue(data, "summary"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private ReviewGateResponse extractGateFromResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            throw new ReviewClientException("Empty response from review gate endpoint");
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return ReviewGateResponse.builder()
                .taskId(parseUuid(data, "taskId"))
                .totalReviews(data.get("totalReviews") instanceof Number n ? n.intValue() : 0)
                .humanApprovals(data.get("humanApprovals") instanceof Number n ? n.intValue() : 0)
                .aiApproval(Boolean.TRUE.equals(data.get("aiApproval")))
                .policyMet(Boolean.TRUE.equals(data.get("policyMet")))
                .build();
    }

    private UUID parseUuid(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    // ---- Inner DTOs ----

    /**
     * Response from create/submit review endpoints.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReviewResponse {
        private UUID id;
        private UUID taskId;
        private String status;
        private String summary;
    }

    /**
     * Request body for an individual review comment.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReviewCommentRequest {
        private String filePath;
        private Integer lineNumber;
        private String body;
        private String severity;
        private String category;
    }

    /**
     * Response from the review gate check endpoint.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReviewGateResponse {
        private UUID taskId;
        private int totalReviews;
        private int humanApprovals;
        private boolean aiApproval;
        private boolean policyMet;
    }

    /**
     * Exception thrown when review service communication fails.
     */
    public static class ReviewClientException extends RuntimeException {
        public ReviewClientException(String message) {
            super(message);
        }

        public ReviewClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
