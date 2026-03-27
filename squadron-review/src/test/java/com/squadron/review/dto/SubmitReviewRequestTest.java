package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmitReviewRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UUID reviewId = UUID.randomUUID();
        ReviewCommentDto comment = ReviewCommentDto.builder()
                .body("Fix this")
                .severity("WARNING")
                .build();

        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(reviewId)
                .status("CHANGES_REQUESTED")
                .summary("Needs work")
                .comments(List.of(comment))
                .build();

        assertEquals(reviewId, request.getReviewId());
        assertEquals("CHANGES_REQUESTED", request.getStatus());
        assertEquals("Needs work", request.getSummary());
        assertEquals(1, request.getComments().size());
    }

    @Test
    void should_allowNullComments() {
        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(UUID.randomUUID())
                .status("APPROVED")
                .build();

        assertNull(request.getComments());
    }

    @Test
    void should_useNoArgsConstructor() {
        SubmitReviewRequest request = new SubmitReviewRequest();
        assertNull(request.getReviewId());
    }

    @Test
    void should_supportSetters() {
        SubmitReviewRequest request = new SubmitReviewRequest();
        UUID reviewId = UUID.randomUUID();
        request.setReviewId(reviewId);
        request.setStatus("APPROVED");
        request.setSummary("All good");

        assertEquals(reviewId, request.getReviewId());
        assertEquals("APPROVED", request.getStatus());
        assertEquals("All good", request.getSummary());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID reviewId = UUID.randomUUID();
        SubmitReviewRequest r1 = SubmitReviewRequest.builder()
                .reviewId(reviewId).status("APPROVED").build();
        SubmitReviewRequest r2 = SubmitReviewRequest.builder()
                .reviewId(reviewId).status("APPROVED").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportToString() {
        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .status("APPROVED")
                .build();
        assertNotNull(request.toString());
    }
}
