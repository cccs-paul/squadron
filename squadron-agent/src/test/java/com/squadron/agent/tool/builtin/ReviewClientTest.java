package com.squadron.agent.tool.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ReviewClient reviewClient;

    @BeforeEach
    void setUp() {
        reviewClient = new ReviewClient(webClient);
    }

    // ---------------------------------------------------------------------------
    // createReview tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void should_createReview_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "id", reviewId.toString(),
                        "taskId", taskId.toString(),
                        "status", "PENDING",
                        "summary", ""
                )
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/reviews"))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        ReviewClient.ReviewResponse result = reviewClient.createReview(tenantId, taskId, "AI");

        assertNotNull(result);
        assertEquals(reviewId, result.getId());
        assertEquals(taskId, result.getTaskId());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_submitReview_successfully() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        List<ReviewClient.ReviewCommentRequest> comments = List.of(
                ReviewClient.ReviewCommentRequest.builder()
                        .filePath("Foo.java").lineNumber(10).body("Issue found")
                        .severity("CRITICAL").category("bug").build()
        );

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "id", reviewId.toString(),
                        "taskId", taskId.toString(),
                        "status", "CHANGES_REQUESTED",
                        "summary", "Found critical issue"
                )
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/reviews/submit"))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        ReviewClient.ReviewResponse result = reviewClient.submitReview(
                reviewId, "CHANGES_REQUESTED", "Found critical issue", comments);

        assertNotNull(result);
        assertEquals(reviewId, result.getId());
        assertEquals("CHANGES_REQUESTED", result.getStatus());
        assertEquals("Found critical issue", result.getSummary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_checkReviewGate_successfully() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "taskId", taskId.toString(),
                        "totalReviews", 3,
                        "humanApprovals", 2,
                        "aiApproval", true,
                        "policyMet", true
                )
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        ReviewClient.ReviewGateResponse result = reviewClient.checkReviewGate(taskId, tenantId, teamId);

        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        assertEquals(3, result.getTotalReviews());
        assertEquals(2, result.getHumanApprovals());
        assertTrue(result.isAiApproval());
        assertTrue(result.isPolicyMet());
    }

    // ---------------------------------------------------------------------------
    // Error handling tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void should_createReview_throwOnError() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/reviews"))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500, "Internal Server Error", null,
                        "server error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(ReviewClient.ReviewClientException.class,
                () -> reviewClient.createReview(tenantId, taskId, "AI"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_submitReview_throwOnError() {
        UUID reviewId = UUID.randomUUID();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/reviews/submit"))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        400, "Bad Request", null,
                        "invalid review".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(ReviewClient.ReviewClientException.class,
                () -> reviewClient.submitReview(reviewId, "APPROVED", "All good",
                        Collections.emptyList()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_checkReviewGate_throwOnError() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404, "Not Found", null,
                        "task not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(ReviewClient.ReviewClientException.class,
                () -> reviewClient.checkReviewGate(taskId, tenantId, teamId));
    }

    // ---------------------------------------------------------------------------
    // Empty response tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void should_handleEmptyResponseOnCreate() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Map<String, Object> emptyResponse = Map.of("success", false);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/reviews"))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(emptyResponse));

        assertThrows(ReviewClient.ReviewClientException.class,
                () -> reviewClient.createReview(tenantId, taskId, "AI"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_handleEmptyResponseOnGate() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Map<String, Object> emptyResponse = Map.of("success", false);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(emptyResponse));

        assertThrows(ReviewClient.ReviewClientException.class,
                () -> reviewClient.checkReviewGate(taskId, tenantId, teamId));
    }

    // ---------------------------------------------------------------------------
    // DTO builder tests
    // ---------------------------------------------------------------------------

    @Test
    void should_reviewResponse_builder() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ReviewClient.ReviewResponse response = ReviewClient.ReviewResponse.builder()
                .id(id)
                .taskId(taskId)
                .status("APPROVED")
                .summary("LGTM")
                .build();

        assertEquals(id, response.getId());
        assertEquals(taskId, response.getTaskId());
        assertEquals("APPROVED", response.getStatus());
        assertEquals("LGTM", response.getSummary());
    }

    @Test
    void should_reviewCommentRequest_builder() {
        ReviewClient.ReviewCommentRequest comment = ReviewClient.ReviewCommentRequest.builder()
                .filePath("Main.java")
                .lineNumber(42)
                .body("Consider null check")
                .severity("MINOR")
                .category("bug")
                .build();

        assertEquals("Main.java", comment.getFilePath());
        assertEquals(42, comment.getLineNumber());
        assertEquals("Consider null check", comment.getBody());
        assertEquals("MINOR", comment.getSeverity());
        assertEquals("bug", comment.getCategory());
    }

    @Test
    void should_reviewGateResponse_builder() {
        UUID taskId = UUID.randomUUID();

        ReviewClient.ReviewGateResponse gate = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .totalReviews(5)
                .humanApprovals(3)
                .aiApproval(true)
                .policyMet(true)
                .build();

        assertEquals(taskId, gate.getTaskId());
        assertEquals(5, gate.getTotalReviews());
        assertEquals(3, gate.getHumanApprovals());
        assertTrue(gate.isAiApproval());
        assertTrue(gate.isPolicyMet());
    }

    @Test
    void should_reviewClientException_withMessage() {
        ReviewClient.ReviewClientException ex =
                new ReviewClient.ReviewClientException("Something went wrong");

        assertEquals("Something went wrong", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void should_reviewClientException_withCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ReviewClient.ReviewClientException ex =
                new ReviewClient.ReviewClientException("Wrapped", cause);

        assertEquals("Wrapped", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
