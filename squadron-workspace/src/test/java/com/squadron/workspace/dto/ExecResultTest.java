package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecResultTest {

    @Test
    void should_buildExecResult() {
        ExecResult result = ExecResult.builder()
                .exitCode(0)
                .stdout("hello world")
                .stderr("")
                .durationMs(150)
                .build();

        assertEquals(0, result.getExitCode());
        assertEquals("hello world", result.getStdout());
        assertEquals("", result.getStderr());
        assertEquals(150, result.getDurationMs());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        ExecResult result = new ExecResult();
        assertEquals(0, result.getExitCode());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
        assertEquals(0, result.getDurationMs());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        ExecResult result = new ExecResult(1, "out", "err", 200);
        assertEquals(1, result.getExitCode());
        assertEquals("out", result.getStdout());
        assertEquals("err", result.getStderr());
        assertEquals(200, result.getDurationMs());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        ExecResult r1 = ExecResult.builder().exitCode(0).stdout("ok").stderr("").durationMs(100).build();
        ExecResult r2 = ExecResult.builder().exitCode(0).stdout("ok").stderr("").durationMs(100).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportSetters() {
        ExecResult result = new ExecResult();
        result.setExitCode(127);
        result.setStdout("output");
        result.setStderr("error");
        result.setDurationMs(500);

        assertEquals(127, result.getExitCode());
        assertEquals("output", result.getStdout());
        assertEquals("error", result.getStderr());
        assertEquals(500, result.getDurationMs());
    }
}
