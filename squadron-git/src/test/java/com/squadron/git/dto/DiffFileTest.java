package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiffFileTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        DiffFile file = DiffFile.builder()
                .filename("src/main/java/App.java")
                .status("modified")
                .additions(25)
                .deletions(10)
                .patch("@@ -1,10 +1,25 @@\n+new line")
                .build();

        assertEquals("src/main/java/App.java", file.getFilename());
        assertEquals("modified", file.getStatus());
        assertEquals(25, file.getAdditions());
        assertEquals(10, file.getDeletions());
        assertEquals("@@ -1,10 +1,25 @@\n+new line", file.getPatch());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        DiffFile file = new DiffFile();
        assertNull(file.getFilename());
        assertNull(file.getStatus());
        assertEquals(0, file.getAdditions());
        assertEquals(0, file.getDeletions());
        assertNull(file.getPatch());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        DiffFile file = new DiffFile("README.md", "added", 100, 0, "patch data");

        assertEquals("README.md", file.getFilename());
        assertEquals("added", file.getStatus());
        assertEquals(100, file.getAdditions());
        assertEquals(0, file.getDeletions());
        assertEquals("patch data", file.getPatch());
    }

    @Test
    void should_supportSettersAndGetters() {
        DiffFile file = new DiffFile();
        file.setFilename("build.gradle");
        file.setStatus("deleted");
        file.setAdditions(0);
        file.setDeletions(50);
        file.setPatch("-removed content");

        assertEquals("build.gradle", file.getFilename());
        assertEquals("deleted", file.getStatus());
        assertEquals(0, file.getAdditions());
        assertEquals(50, file.getDeletions());
        assertEquals("-removed content", file.getPatch());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        DiffFile file1 = DiffFile.builder()
                .filename("App.java")
                .status("modified")
                .additions(10)
                .deletions(5)
                .patch("patch")
                .build();
        DiffFile file2 = DiffFile.builder()
                .filename("App.java")
                .status("modified")
                .additions(10)
                .deletions(5)
                .patch("patch")
                .build();

        assertEquals(file1, file2);
        assertEquals(file1.hashCode(), file2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFilename() {
        DiffFile file1 = DiffFile.builder()
                .filename("FileA.java")
                .status("modified")
                .additions(10)
                .deletions(5)
                .build();
        DiffFile file2 = DiffFile.builder()
                .filename("FileB.java")
                .status("modified")
                .additions(10)
                .deletions(5)
                .build();

        assertNotEquals(file1, file2);
    }

    @Test
    void should_notBeEqual_when_differentAdditions() {
        DiffFile file1 = DiffFile.builder()
                .filename("App.java")
                .additions(10)
                .build();
        DiffFile file2 = DiffFile.builder()
                .filename("App.java")
                .additions(20)
                .build();

        assertNotEquals(file1, file2);
    }

    @Test
    void should_supportToString() {
        DiffFile file = DiffFile.builder()
                .filename("test.java")
                .status("modified")
                .additions(5)
                .deletions(3)
                .patch("diff content")
                .build();

        String str = file.toString();
        assertNotNull(str);
        assertTrue(str.contains("filename"));
        assertTrue(str.contains("status"));
        assertTrue(str.contains("additions"));
        assertTrue(str.contains("deletions"));
        assertTrue(str.contains("patch"));
    }

    @Test
    void should_handleZeroAdditionsAndDeletions() {
        DiffFile file = DiffFile.builder()
                .filename("unchanged.java")
                .status("renamed")
                .additions(0)
                .deletions(0)
                .build();

        assertEquals(0, file.getAdditions());
        assertEquals(0, file.getDeletions());
    }
}
