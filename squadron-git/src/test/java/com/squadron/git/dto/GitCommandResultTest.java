package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitCommandResultTest {

    @Test
    void should_createWithBuilder_when_successResult() {
        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("Already up to date.")
                .errorOutput("")
                .exitCode(0)
                .build();

        assertTrue(result.isSuccess());
        assertEquals("Already up to date.", result.getOutput());
        assertEquals("", result.getErrorOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_createWithBuilder_when_failureResult() {
        GitCommandResult result = GitCommandResult.builder()
                .success(false)
                .output("")
                .errorOutput("fatal: not a git repository")
                .exitCode(128)
                .build();

        assertFalse(result.isSuccess());
        assertEquals("", result.getOutput());
        assertEquals("fatal: not a git repository", result.getErrorOutput());
        assertEquals(128, result.getExitCode());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        GitCommandResult result = new GitCommandResult();
        assertFalse(result.isSuccess());
        assertNull(result.getOutput());
        assertNull(result.getErrorOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        GitCommandResult result = new GitCommandResult(true, "output text", "error text", 1);

        assertTrue(result.isSuccess());
        assertEquals("output text", result.getOutput());
        assertEquals("error text", result.getErrorOutput());
        assertEquals(1, result.getExitCode());
    }

    @Test
    void should_supportSettersAndGetters() {
        GitCommandResult result = new GitCommandResult();
        result.setSuccess(true);
        result.setOutput("Branch created");
        result.setErrorOutput("");
        result.setExitCode(0);

        assertTrue(result.isSuccess());
        assertEquals("Branch created", result.getOutput());
        assertEquals("", result.getErrorOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        GitCommandResult result1 = GitCommandResult.builder()
                .success(true)
                .output("done")
                .errorOutput("")
                .exitCode(0)
                .build();
        GitCommandResult result2 = GitCommandResult.builder()
                .success(true)
                .output("done")
                .errorOutput("")
                .exitCode(0)
                .build();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentSuccess() {
        GitCommandResult result1 = GitCommandResult.builder()
                .success(true)
                .output("")
                .exitCode(0)
                .build();
        GitCommandResult result2 = GitCommandResult.builder()
                .success(false)
                .output("")
                .exitCode(0)
                .build();

        assertNotEquals(result1, result2);
    }

    @Test
    void should_notBeEqual_when_differentExitCode() {
        GitCommandResult result1 = GitCommandResult.builder()
                .success(false)
                .exitCode(1)
                .build();
        GitCommandResult result2 = GitCommandResult.builder()
                .success(false)
                .exitCode(128)
                .build();

        assertNotEquals(result1, result2);
    }

    @Test
    void should_supportToString() {
        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("output")
                .errorOutput("error")
                .exitCode(0)
                .build();

        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("success"));
        assertTrue(str.contains("output"));
        assertTrue(str.contains("errorOutput"));
        assertTrue(str.contains("exitCode"));
    }

    @Test
    void should_handleNullOutputFields() {
        GitCommandResult result = GitCommandResult.builder()
                .success(false)
                .exitCode(1)
                .build();

        assertNull(result.getOutput());
        assertNull(result.getErrorOutput());
    }
}
