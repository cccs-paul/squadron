package com.squadron.agent.tool.builtin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecResultDtoTest {

    @Test
    void should_buildSuccessResult_when_usingBuilder() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0)
                .stdout("Hello, world!\n")
                .stderr("")
                .build();

        assertEquals(0, result.getExitCode());
        assertEquals("Hello, world!\n", result.getStdout());
        assertEquals("", result.getStderr());
    }

    @Test
    void should_buildErrorResult_when_usingBuilder() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(1)
                .stdout("")
                .stderr("command not found: foo")
                .build();

        assertEquals(1, result.getExitCode());
        assertEquals("", result.getStdout());
        assertEquals("command not found: foo", result.getStderr());
    }

    @Test
    void should_createResult_when_usingNoArgsConstructor() {
        ExecResultDto result = new ExecResultDto();

        assertEquals(0, result.getExitCode());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    @Test
    void should_createResult_when_usingAllArgsConstructor() {
        ExecResultDto result = new ExecResultDto(0, "output line", "warning line");

        assertEquals(0, result.getExitCode());
        assertEquals("output line", result.getStdout());
        assertEquals("warning line", result.getStderr());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ExecResultDto result = new ExecResultDto();

        result.setExitCode(127);
        result.setStdout("");
        result.setStderr("/bin/sh: mvn: not found");

        assertEquals(127, result.getExitCode());
        assertEquals("", result.getStdout());
        assertEquals("/bin/sh: mvn: not found", result.getStderr());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        ExecResultDto r1 = ExecResultDto.builder()
                .exitCode(0).stdout("ok").stderr("").build();
        ExecResultDto r2 = ExecResultDto.builder()
                .exitCode(0).stdout("ok").stderr("").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentExitCode() {
        ExecResultDto r1 = ExecResultDto.builder().exitCode(0).stdout("ok").build();
        ExecResultDto r2 = ExecResultDto.builder().exitCode(1).stdout("ok").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_notBeEqual_when_differentStdout() {
        ExecResultDto r1 = ExecResultDto.builder().exitCode(0).stdout("output A").build();
        ExecResultDto r2 = ExecResultDto.builder().exitCode(0).stdout("output B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_notBeEqual_when_differentStderr() {
        ExecResultDto r1 = ExecResultDto.builder().exitCode(1).stderr("error A").build();
        ExecResultDto r2 = ExecResultDto.builder().exitCode(1).stderr("error B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0)
                .stdout("build success")
                .stderr("")
                .build();
        String toString = result.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("build success"));
        assertTrue(toString.contains("0"));
    }

    @Test
    void should_handleNonZeroExitCodes_when_commandFails() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(137)
                .stdout("")
                .stderr("Killed")
                .build();

        assertEquals(137, result.getExitCode());
        assertEquals("Killed", result.getStderr());
    }

    @Test
    void should_handleNullOutputs_when_notSet() {
        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0)
                .build();

        assertEquals(0, result.getExitCode());
        assertNull(result.getStdout());
        assertNull(result.getStderr());
    }

    @Test
    void should_handleMultilineOutput_when_commandProducesMultipleLines() {
        String multilineStdout = "line1\nline2\nline3\n";
        String multilineStderr = "warning1\nwarning2\n";

        ExecResultDto result = ExecResultDto.builder()
                .exitCode(0)
                .stdout(multilineStdout)
                .stderr(multilineStderr)
                .build();

        assertEquals(multilineStdout, result.getStdout());
        assertEquals(multilineStderr, result.getStderr());
        assertTrue(result.getStdout().contains("line2"));
    }
}
