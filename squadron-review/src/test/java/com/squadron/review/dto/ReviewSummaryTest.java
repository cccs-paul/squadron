package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSummaryTest {

    @Test
    void should_buildSummary_when_usingBuilder() {
        UUID taskId = UUID.randomUUID();
        ReviewDto review = ReviewDto.builder()
                .id(UUID.randomUUID())
                .status("APPROVED")
                .build();

        ReviewSummary summary = ReviewSummary.builder()
                .taskId(taskId)
                .totalReviews(3)
                .humanApprovals(2)
                .aiApproval(true)
                .policyMet(true)
                .reviews(List.of(review))
                .build();

        assertEquals(taskId, summary.getTaskId());
        assertEquals(3, summary.getTotalReviews());
        assertEquals(2, summary.getHumanApprovals());
        assertTrue(summary.isAiApproval());
        assertTrue(summary.isPolicyMet());
        assertEquals(1, summary.getReviews().size());
    }

    @Test
    void should_defaultBooleansFalse_when_notSet() {
        ReviewSummary summary = ReviewSummary.builder()
                .taskId(UUID.randomUUID())
                .build();

        assertFalse(summary.isAiApproval());
        assertFalse(summary.isPolicyMet());
        assertEquals(0, summary.getTotalReviews());
        assertEquals(0, summary.getHumanApprovals());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewSummary summary = new ReviewSummary();
        assertNull(summary.getTaskId());
    }

    @Test
    void should_supportSetters() {
        ReviewSummary summary = new ReviewSummary();
        UUID taskId = UUID.randomUUID();
        summary.setTaskId(taskId);
        summary.setPolicyMet(true);
        summary.setTotalReviews(5);

        assertEquals(taskId, summary.getTaskId());
        assertTrue(summary.isPolicyMet());
        assertEquals(5, summary.getTotalReviews());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID taskId = UUID.randomUUID();
        ReviewSummary s1 = ReviewSummary.builder().taskId(taskId).totalReviews(1).policyMet(true).build();
        ReviewSummary s2 = ReviewSummary.builder().taskId(taskId).totalReviews(1).policyMet(true).build();

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void should_supportToString() {
        ReviewSummary summary = ReviewSummary.builder().build();
        assertNotNull(summary.toString());
    }
}
