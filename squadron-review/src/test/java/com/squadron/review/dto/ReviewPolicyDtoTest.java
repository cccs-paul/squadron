package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewPolicyDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewPolicyDto dto = ReviewPolicyDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .selfReviewAllowed(false)
                .autoRequestReviewers("[\"admin\"]")
                .reviewChecklist("[\"security\"]")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(2, dto.getMinHumanApprovals());
        assertEquals(true, dto.getRequireAiReview());
        assertEquals(false, dto.getSelfReviewAllowed());
        assertEquals("[\"admin\"]", dto.getAutoRequestReviewers());
        assertEquals("[\"security\"]", dto.getReviewChecklist());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewPolicyDto dto = new ReviewPolicyDto();
        assertNull(dto.getId());
    }

    @Test
    void should_supportSetters() {
        ReviewPolicyDto dto = new ReviewPolicyDto();
        dto.setMinHumanApprovals(3);
        dto.setRequireAiReview(false);

        assertEquals(3, dto.getMinHumanApprovals());
        assertEquals(false, dto.getRequireAiReview());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        ReviewPolicyDto d1 = ReviewPolicyDto.builder().tenantId(tenantId).minHumanApprovals(1).build();
        ReviewPolicyDto d2 = ReviewPolicyDto.builder().tenantId(tenantId).minHumanApprovals(1).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_supportToString() {
        ReviewPolicyDto dto = ReviewPolicyDto.builder().build();
        assertNotNull(dto.toString());
    }
}
