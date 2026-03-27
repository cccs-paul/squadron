package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewCommentDto comment = ReviewCommentDto.builder()
                .body("Comment")
                .build();

        ReviewDto dto = ReviewDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .summary("LGTM")
                .createdAt(now)
                .updatedAt(now)
                .comments(List.of(comment))
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(reviewerId, dto.getReviewerId());
        assertEquals("HUMAN", dto.getReviewerType());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals("LGTM", dto.getSummary());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
        assertEquals(1, dto.getComments().size());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewDto dto = new ReviewDto();
        assertNull(dto.getId());
    }

    @Test
    void should_supportSetters() {
        ReviewDto dto = new ReviewDto();
        dto.setStatus("CHANGES_REQUESTED");
        dto.setSummary("Fix issues");

        assertEquals("CHANGES_REQUESTED", dto.getStatus());
        assertEquals("Fix issues", dto.getSummary());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        ReviewDto d1 = ReviewDto.builder().id(id).status("APPROVED").build();
        ReviewDto d2 = ReviewDto.builder().id(id).status("APPROVED").build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_supportToString() {
        ReviewDto dto = ReviewDto.builder().status("PENDING").build();
        assertNotNull(dto.toString());
    }
}
