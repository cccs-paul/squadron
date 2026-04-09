package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGitAccessResultTest {

    @Test
    void should_buildSuccessResult() {
        TestGitAccessResult result = TestGitAccessResult.builder()
                .success(true)
                .message("Git repository is accessible")
                .branch("main")
                .durationMs(1500)
                .build();

        assertTrue(result.isSuccess());
        assertEquals("Git repository is accessible", result.getMessage());
        assertEquals("main", result.getBranch());
        assertEquals(1500, result.getDurationMs());
    }

    @Test
    void should_buildFailureResult() {
        TestGitAccessResult result = TestGitAccessResult.builder()
                .success(false)
                .message("Git clone failed: repository not found")
                .branch(null)
                .durationMs(3000)
                .build();

        assertFalse(result.isSuccess());
        assertEquals("Git clone failed: repository not found", result.getMessage());
        assertNull(result.getBranch());
        assertEquals(3000, result.getDurationMs());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TestGitAccessResult result = new TestGitAccessResult();
        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
        assertNull(result.getBranch());
        assertEquals(0, result.getDurationMs());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        TestGitAccessResult result = new TestGitAccessResult(true, "OK", "develop", 500);

        assertTrue(result.isSuccess());
        assertEquals("OK", result.getMessage());
        assertEquals("develop", result.getBranch());
        assertEquals(500, result.getDurationMs());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        TestGitAccessResult r1 = TestGitAccessResult.builder()
                .success(true).message("OK").branch("main").durationMs(100).build();
        TestGitAccessResult r2 = TestGitAccessResult.builder()
                .success(true).message("OK").branch("main").durationMs(100).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportToString() {
        TestGitAccessResult result = TestGitAccessResult.builder()
                .success(true)
                .message("Git repository is accessible")
                .branch("main")
                .durationMs(1500)
                .build();

        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("success"));
        assertTrue(str.contains("message"));
    }

    @Test
    void should_supportSetters() {
        TestGitAccessResult result = new TestGitAccessResult();
        result.setSuccess(true);
        result.setMessage("OK");
        result.setBranch("main");
        result.setDurationMs(250);

        assertTrue(result.isSuccess());
        assertEquals("OK", result.getMessage());
        assertEquals("main", result.getBranch());
        assertEquals(250, result.getDurationMs());
    }
}
