package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Map<String, Object> comments = Map.of("line_42", "Consider using a constant here");
        Instant now = Instant.now();

        ReviewDto dto = ReviewDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("AI")
                .status("APPROVED")
                .comments(comments)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(reviewerId, dto.getReviewerId());
        assertEquals("AI", dto.getReviewerType());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals(comments, dto.getComments());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ReviewDto dto = new ReviewDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getReviewerId());
        assertNull(dto.getReviewerType());
        assertNull(dto.getStatus());
        assertNull(dto.getComments());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Map<String, Object> comments = Map.of("file", "src/Main.java");
        Instant created = Instant.now();
        Instant updated = Instant.now();

        ReviewDto dto = new ReviewDto(
                id, tenantId, taskId, reviewerId, "HUMAN",
                "CHANGES_REQUESTED", comments, created, updated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(reviewerId, dto.getReviewerId());
        assertEquals("HUMAN", dto.getReviewerType());
        assertEquals("CHANGES_REQUESTED", dto.getStatus());
        assertEquals(comments, dto.getComments());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ReviewDto dto = new ReviewDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setReviewerType("AI");
        dto.setStatus("PENDING");

        assertEquals(id, dto.getId());
        assertEquals("AI", dto.getReviewerType());
        assertEquals("PENDING", dto.getStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ReviewDto dto1 = ReviewDto.builder()
                .id(id)
                .tenantId(tenantId)
                .status("APPROVED")
                .build();

        ReviewDto dto2 = ReviewDto.builder()
                .id(id)
                .tenantId(tenantId)
                .status("APPROVED")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ReviewDto dto1 = ReviewDto.builder()
                .status("APPROVED")
                .build();

        ReviewDto dto2 = ReviewDto.builder()
                .status("REJECTED")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        ReviewDto dto = ReviewDto.builder()
                .reviewerType("AI")
                .status("APPROVED")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("AI"));
        assertTrue(str.contains("APPROVED"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        ReviewDto dto = ReviewDto.builder()
                .id(null)
                .tenantId(null)
                .taskId(null)
                .reviewerId(null)
                .comments(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getReviewerId());
        assertNull(dto.getComments());
    }

    @Test
    void should_handleEmptyComments_when_emptyMapProvided() {
        ReviewDto dto = ReviewDto.builder()
                .comments(Map.of())
                .build();

        assertNotNull(dto.getComments());
        assertTrue(dto.getComments().isEmpty());
    }
}
