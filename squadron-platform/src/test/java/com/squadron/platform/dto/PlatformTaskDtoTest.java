package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTaskDtoTest {

    @Test
    void should_buildWithAllFields() {
        Instant now = Instant.now();
        PlatformTaskDto dto = PlatformTaskDto.builder()
                .externalId("PROJ-123")
                .externalUrl("https://jira.example.com/browse/PROJ-123")
                .title("Fix bug")
                .description("Description of the bug")
                .status("IN_PROGRESS")
                .priority("HIGH")
                .assignee("john.doe")
                .labels(List.of("bug", "critical"))
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals("PROJ-123", dto.getExternalId());
        assertEquals("https://jira.example.com/browse/PROJ-123", dto.getExternalUrl());
        assertEquals("Fix bug", dto.getTitle());
        assertEquals("Description of the bug", dto.getDescription());
        assertEquals("IN_PROGRESS", dto.getStatus());
        assertEquals("HIGH", dto.getPriority());
        assertEquals("john.doe", dto.getAssignee());
        assertEquals(List.of("bug", "critical"), dto.getLabels());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_supportNoArgsConstructor() {
        PlatformTaskDto dto = new PlatformTaskDto();
        assertNull(dto.getExternalId());
        assertNull(dto.getTitle());
    }

    @Test
    void should_supportSetters() {
        PlatformTaskDto dto = new PlatformTaskDto();
        dto.setExternalId("ISSUE-1");
        dto.setTitle("Test issue");
        dto.setStatus("OPEN");

        assertEquals("ISSUE-1", dto.getExternalId());
        assertEquals("Test issue", dto.getTitle());
        assertEquals("OPEN", dto.getStatus());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        Instant now = Instant.now();
        PlatformTaskDto d1 = PlatformTaskDto.builder()
                .externalId("PROJ-1")
                .title("Task")
                .createdAt(now)
                .build();
        PlatformTaskDto d2 = PlatformTaskDto.builder()
                .externalId("PROJ-1")
                .title("Task")
                .createdAt(now)
                .build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_implementToString() {
        PlatformTaskDto dto = PlatformTaskDto.builder()
                .externalId("PROJ-123")
                .title("Test")
                .build();
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("PROJ-123"));
    }
}
