package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffResultTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        DiffFile file1 = DiffFile.builder()
                .filename("App.java")
                .status("modified")
                .additions(10)
                .deletions(3)
                .build();
        DiffFile file2 = DiffFile.builder()
                .filename("README.md")
                .status("added")
                .additions(20)
                .deletions(0)
                .build();

        DiffResult result = DiffResult.builder()
                .files(List.of(file1, file2))
                .totalAdditions(30)
                .totalDeletions(3)
                .build();

        assertEquals(2, result.getFiles().size());
        assertEquals(30, result.getTotalAdditions());
        assertEquals(3, result.getTotalDeletions());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        DiffResult result = new DiffResult();
        assertNull(result.getFiles());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(0, result.getTotalDeletions());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        List<DiffFile> files = List.of(
                DiffFile.builder().filename("test.java").build());

        DiffResult result = new DiffResult(files, 15, 5);

        assertEquals(1, result.getFiles().size());
        assertEquals(15, result.getTotalAdditions());
        assertEquals(5, result.getTotalDeletions());
    }

    @Test
    void should_supportSettersAndGetters() {
        DiffResult result = new DiffResult();
        List<DiffFile> files = new ArrayList<>();
        files.add(DiffFile.builder().filename("a.java").build());

        result.setFiles(files);
        result.setTotalAdditions(42);
        result.setTotalDeletions(7);

        assertEquals(1, result.getFiles().size());
        assertEquals(42, result.getTotalAdditions());
        assertEquals(7, result.getTotalDeletions());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        List<DiffFile> files = List.of(
                DiffFile.builder().filename("App.java").status("modified").additions(5).deletions(2).build());

        DiffResult result1 = DiffResult.builder()
                .files(files)
                .totalAdditions(5)
                .totalDeletions(2)
                .build();
        DiffResult result2 = DiffResult.builder()
                .files(files)
                .totalAdditions(5)
                .totalDeletions(2)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentTotals() {
        DiffResult result1 = DiffResult.builder()
                .files(List.of())
                .totalAdditions(10)
                .totalDeletions(5)
                .build();
        DiffResult result2 = DiffResult.builder()
                .files(List.of())
                .totalAdditions(20)
                .totalDeletions(5)
                .build();

        assertNotEquals(result1, result2);
    }

    @Test
    void should_supportToString() {
        DiffResult result = DiffResult.builder()
                .files(List.of())
                .totalAdditions(10)
                .totalDeletions(3)
                .build();

        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("files"));
        assertTrue(str.contains("totalAdditions"));
        assertTrue(str.contains("totalDeletions"));
    }

    @Test
    void should_handleEmptyFilesList() {
        DiffResult result = DiffResult.builder()
                .files(List.of())
                .totalAdditions(0)
                .totalDeletions(0)
                .build();

        assertNotNull(result.getFiles());
        assertTrue(result.getFiles().isEmpty());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(0, result.getTotalDeletions());
    }

    @Test
    void should_handleNullFilesList() {
        DiffResult result = DiffResult.builder()
                .totalAdditions(0)
                .totalDeletions(0)
                .build();

        assertNull(result.getFiles());
    }
}
