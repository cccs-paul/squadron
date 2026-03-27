package com.squadron.review.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewCommentTest {

    @Test
    void should_buildReviewComment_when_usingBuilder() {
        UUID reviewId = UUID.randomUUID();

        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId)
                .filePath("src/main/java/App.java")
                .lineNumber(42)
                .body("Consider null check here")
                .severity("WARNING")
                .category("BUG")
                .build();

        assertEquals(reviewId, comment.getReviewId());
        assertEquals("src/main/java/App.java", comment.getFilePath());
        assertEquals(42, comment.getLineNumber());
        assertEquals("Consider null check here", comment.getBody());
        assertEquals("WARNING", comment.getSeverity());
        assertEquals("BUG", comment.getCategory());
    }

    @Test
    void should_allowNullFilePath_when_generalComment() {
        ReviewComment comment = ReviewComment.builder()
                .reviewId(UUID.randomUUID())
                .body("General observation")
                .build();

        assertNull(comment.getFilePath());
        assertNull(comment.getLineNumber());
        assertEquals("General observation", comment.getBody());
    }

    @Test
    void should_setTimestamp_when_onCreateCalled() {
        ReviewComment comment = ReviewComment.builder()
                .reviewId(UUID.randomUUID())
                .body("Test comment")
                .build();

        comment.onCreate();

        assertNotNull(comment.getCreatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewComment comment = new ReviewComment();
        assertNull(comment.getId());
        assertNull(comment.getReviewId());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewComment comment = new ReviewComment(id, reviewId, "path/file.java", 10, "body", "INFO", "SECURITY", now);

        assertEquals(id, comment.getId());
        assertEquals(reviewId, comment.getReviewId());
        assertEquals("path/file.java", comment.getFilePath());
        assertEquals(10, comment.getLineNumber());
        assertEquals("body", comment.getBody());
        assertEquals("INFO", comment.getSeverity());
        assertEquals("SECURITY", comment.getCategory());
        assertEquals(now, comment.getCreatedAt());
    }

    @Test
    void should_supportSetters() {
        ReviewComment comment = new ReviewComment();
        comment.setBody("Updated body");
        comment.setSeverity("CRITICAL");
        comment.setCategory("PERFORMANCE");

        assertEquals("Updated body", comment.getBody());
        assertEquals("CRITICAL", comment.getSeverity());
        assertEquals("PERFORMANCE", comment.getCategory());
    }
}
