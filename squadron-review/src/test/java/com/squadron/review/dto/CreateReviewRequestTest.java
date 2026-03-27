package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateReviewRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        CreateReviewRequest request = CreateReviewRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(reviewerId, request.getReviewerId());
        assertEquals("HUMAN", request.getReviewerType());
    }

    @Test
    void should_allowNullReviewerId_when_aiReview() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("AI")
                .build();

        assertNull(request.getReviewerId());
        assertEquals("AI", request.getReviewerType());
    }

    @Test
    void should_useNoArgsConstructor() {
        CreateReviewRequest request = new CreateReviewRequest();
        assertNull(request.getTenantId());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        CreateReviewRequest request = new CreateReviewRequest(tenantId, taskId, reviewerId, "HUMAN");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(reviewerId, request.getReviewerId());
        assertEquals("HUMAN", request.getReviewerType());
    }

    @Test
    void should_supportSetters() {
        CreateReviewRequest request = new CreateReviewRequest();
        UUID taskId = UUID.randomUUID();
        request.setTaskId(taskId);
        request.setReviewerType("AI");

        assertEquals(taskId, request.getTaskId());
        assertEquals("AI", request.getReviewerType());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreateReviewRequest r1 = CreateReviewRequest.builder()
                .tenantId(tenantId).taskId(taskId).reviewerType("HUMAN").build();
        CreateReviewRequest r2 = CreateReviewRequest.builder()
                .tenantId(tenantId).taskId(taskId).reviewerType("HUMAN").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportToString() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .reviewerType("AI")
                .build();

        assertNotNull(request.toString());
    }
}
