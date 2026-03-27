package com.squadron.review.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewTest {

    @Test
    void should_buildReview_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Review review = Review.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .summary("Looks good")
                .build();

        assertEquals(tenantId, review.getTenantId());
        assertEquals(taskId, review.getTaskId());
        assertEquals(reviewerId, review.getReviewerId());
        assertEquals("HUMAN", review.getReviewerType());
        assertEquals("PENDING", review.getStatus());
        assertEquals("Looks good", review.getSummary());
    }

    @Test
    void should_setDefaultStatus_when_notExplicitlySet() {
        Review review = Review.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("AI")
                .build();

        assertEquals("PENDING", review.getStatus());
    }

    @Test
    void should_allowNullReviewerId_when_aiReview() {
        Review review = Review.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("AI")
                .build();

        assertNull(review.getReviewerId());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        Review review = Review.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("HUMAN")
                .build();

        review.onCreate();

        assertNotNull(review.getCreatedAt());
        assertNotNull(review.getUpdatedAt());
        assertEquals(review.getCreatedAt(), review.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        Review review = Review.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("HUMAN")
                .build();

        review.onCreate();
        Instant originalUpdated = review.getUpdatedAt();

        Thread.sleep(10);
        review.onUpdate();

        assertNotNull(review.getUpdatedAt());
        assertEquals(review.getCreatedAt(), review.getCreatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        Review review = new Review();
        assertNull(review.getId());
        assertNull(review.getTenantId());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Instant now = Instant.now();

        Review review = new Review(id, tenantId, taskId, reviewerId, "HUMAN", "APPROVED", "Good", now, now);

        assertEquals(id, review.getId());
        assertEquals(tenantId, review.getTenantId());
        assertEquals(taskId, review.getTaskId());
        assertEquals(reviewerId, review.getReviewerId());
        assertEquals("HUMAN", review.getReviewerType());
        assertEquals("APPROVED", review.getStatus());
        assertEquals("Good", review.getSummary());
        assertEquals(now, review.getCreatedAt());
        assertEquals(now, review.getUpdatedAt());
    }

    @Test
    void should_supportSetters() {
        Review review = new Review();
        UUID id = UUID.randomUUID();
        review.setId(id);
        review.setStatus("APPROVED");

        assertEquals(id, review.getId());
        assertEquals("APPROVED", review.getStatus());
    }
}
