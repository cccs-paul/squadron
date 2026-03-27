package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MergeabilityDtoTest {

    @Test
    void should_createWithBuilder() {
        MergeabilityDto dto = MergeabilityDto.builder()
                .mergeable(true)
                .conflictFiles(List.of("file1.java", "file2.java"))
                .build();

        assertTrue(dto.isMergeable());
        assertEquals(2, dto.getConflictFiles().size());
        assertEquals("file1.java", dto.getConflictFiles().get(0));
        assertEquals("file2.java", dto.getConflictFiles().get(1));
    }

    @Test
    void should_createWithNoArgsConstructor() {
        MergeabilityDto dto = new MergeabilityDto();
        assertFalse(dto.isMergeable());
        // @Builder.Default initializes conflictFiles to List.of() even in no-args constructor
        assertNotNull(dto.getConflictFiles());
        assertTrue(dto.getConflictFiles().isEmpty());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        List<String> files = List.of("a.java");
        MergeabilityDto dto = new MergeabilityDto(true, files);

        assertTrue(dto.isMergeable());
        assertEquals(1, dto.getConflictFiles().size());
    }

    @Test
    void should_supportSettersAndGetters() {
        MergeabilityDto dto = new MergeabilityDto();
        dto.setMergeable(true);
        dto.setConflictFiles(List.of("x.java"));

        assertTrue(dto.isMergeable());
        assertEquals(List.of("x.java"), dto.getConflictFiles());
    }

    @Test
    void should_haveDefaultConflictFiles_when_usingBuilder() {
        MergeabilityDto dto = MergeabilityDto.builder()
                .mergeable(false)
                .build();

        assertFalse(dto.isMergeable());
        assertNotNull(dto.getConflictFiles());
        assertTrue(dto.getConflictFiles().isEmpty());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        MergeabilityDto dto1 = MergeabilityDto.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();
        MergeabilityDto dto2 = MergeabilityDto.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_supportToString() {
        MergeabilityDto dto = MergeabilityDto.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("mergeable"));
    }
}
