package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeGenerationResultTest {

    @Test
    void should_buildSuccessResult_when_usingBuilder() {
        CodeGenerationResult result = CodeGenerationResult.builder()
                .success(true)
                .branchName("feature/TASK-123-login")
                .prUrl("https://github.com/org/repo/pull/42")
                .prId("42")
                .build();

        assertTrue(result.isSuccess());
        assertEquals("feature/TASK-123-login", result.getBranchName());
        assertEquals("https://github.com/org/repo/pull/42", result.getPrUrl());
        assertEquals("42", result.getPrId());
        assertNull(result.getError());
    }

    @Test
    void should_buildErrorResult_when_usingBuilder() {
        CodeGenerationResult result = CodeGenerationResult.builder()
                .success(false)
                .error("Push failed: authentication error")
                .build();

        assertFalse(result.isSuccess());
        assertNull(result.getBranchName());
        assertNull(result.getPrUrl());
        assertNull(result.getPrId());
        assertEquals("Push failed: authentication error", result.getError());
    }

    @Test
    void should_createResult_when_usingNoArgsConstructor() {
        CodeGenerationResult result = new CodeGenerationResult();

        assertFalse(result.isSuccess());
        assertNull(result.getBranchName());
        assertNull(result.getPrUrl());
        assertNull(result.getPrId());
        assertNull(result.getError());
    }

    @Test
    void should_createResult_when_usingAllArgsConstructor() {
        CodeGenerationResult result = new CodeGenerationResult(
                true, "feature/TASK-456", "https://github.com/org/repo/pull/99", "99", null
        );

        assertTrue(result.isSuccess());
        assertEquals("feature/TASK-456", result.getBranchName());
        assertEquals("https://github.com/org/repo/pull/99", result.getPrUrl());
        assertEquals("99", result.getPrId());
        assertNull(result.getError());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        CodeGenerationResult result = new CodeGenerationResult();

        result.setSuccess(true);
        result.setBranchName("fix/TASK-789");
        result.setPrUrl("https://gitlab.com/org/repo/-/merge_requests/10");
        result.setPrId("10");
        result.setError(null);

        assertTrue(result.isSuccess());
        assertEquals("fix/TASK-789", result.getBranchName());
        assertEquals("https://gitlab.com/org/repo/-/merge_requests/10", result.getPrUrl());
        assertEquals("10", result.getPrId());
        assertNull(result.getError());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        CodeGenerationResult r1 = CodeGenerationResult.builder()
                .success(true).branchName("branch").prUrl("url").prId("1").build();
        CodeGenerationResult r2 = CodeGenerationResult.builder()
                .success(true).branchName("branch").prUrl("url").prId("1").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        CodeGenerationResult r1 = CodeGenerationResult.builder()
                .success(true).prId("1").build();
        CodeGenerationResult r2 = CodeGenerationResult.builder()
                .success(true).prId("2").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_notBeEqual_when_differentSuccess() {
        CodeGenerationResult r1 = CodeGenerationResult.builder().success(true).build();
        CodeGenerationResult r2 = CodeGenerationResult.builder().success(false).build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        CodeGenerationResult result = CodeGenerationResult.builder()
                .success(true)
                .branchName("feature/test")
                .prUrl("https://github.com/test")
                .build();
        String toString = result.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("feature/test"));
        assertTrue(toString.contains("https://github.com/test"));
    }
}
