package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewCommentDtoTest {

    @Test
    void should_buildDto_when_usingBuilder() {
        ReviewCommentDto dto = ReviewCommentDto.builder()
                .filePath("src/main/java/App.java")
                .lineNumber(42)
                .body("Potential NPE")
                .severity("CRITICAL")
                .category("BUG")
                .build();

        assertEquals("src/main/java/App.java", dto.getFilePath());
        assertEquals(42, dto.getLineNumber());
        assertEquals("Potential NPE", dto.getBody());
        assertEquals("CRITICAL", dto.getSeverity());
        assertEquals("BUG", dto.getCategory());
    }

    @Test
    void should_allowNullOptionalFields() {
        ReviewCommentDto dto = ReviewCommentDto.builder()
                .body("General comment")
                .build();

        assertNull(dto.getFilePath());
        assertNull(dto.getLineNumber());
        assertNull(dto.getSeverity());
        assertNull(dto.getCategory());
        assertEquals("General comment", dto.getBody());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewCommentDto dto = new ReviewCommentDto();
        assertNull(dto.getBody());
    }

    @Test
    void should_useAllArgsConstructor() {
        ReviewCommentDto dto = new ReviewCommentDto("file.java", 10, "body", "INFO", "DOCUMENTATION");

        assertEquals("file.java", dto.getFilePath());
        assertEquals(10, dto.getLineNumber());
        assertEquals("body", dto.getBody());
        assertEquals("INFO", dto.getSeverity());
        assertEquals("DOCUMENTATION", dto.getCategory());
    }

    @Test
    void should_supportSetters() {
        ReviewCommentDto dto = new ReviewCommentDto();
        dto.setBody("Updated");
        dto.setSeverity("WARNING");

        assertEquals("Updated", dto.getBody());
        assertEquals("WARNING", dto.getSeverity());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        ReviewCommentDto d1 = ReviewCommentDto.builder().body("test").build();
        ReviewCommentDto d2 = ReviewCommentDto.builder().body("test").build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_supportToString() {
        ReviewCommentDto dto = ReviewCommentDto.builder().body("test").build();
        assertNotNull(dto.toString());
    }
}
