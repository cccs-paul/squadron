package com.squadron.agent.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {

    private final SystemPromptBuilder promptBuilder = new SystemPromptBuilder();

    @Test
    void should_buildPlanningPrompt_when_calledWithTaskInfo() {
        String result = promptBuilder.buildPlanningPrompt("Add login feature", "Implement OAuth login");

        assertNotNull(result);
        assertTrue(result.contains("Add login feature"));
        assertTrue(result.contains("Implement OAuth login"));
        assertTrue(result.contains("senior software architect"));
        assertTrue(result.contains("planning agent"));
    }

    @Test
    void should_containInstructions_when_planningPromptBuilt() {
        String result = promptBuilder.buildPlanningPrompt("Task", "Description");

        assertTrue(result.contains("Analyze the task requirements"));
        assertTrue(result.contains("Break down the work"));
        assertTrue(result.contains("testing strategy"));
    }

    @Test
    void should_buildCodingPrompt_when_calledWithPlanAndTask() {
        String result = promptBuilder.buildCodingPrompt("1. Create entity\n2. Create repository", "Add users API");

        assertNotNull(result);
        assertTrue(result.contains("Add users API"));
        assertTrue(result.contains("Create entity"));
        assertTrue(result.contains("expert software engineer"));
        assertTrue(result.contains("coding agent"));
    }

    @Test
    void should_containCodingGuidelines_when_codingPromptBuilt() {
        String result = promptBuilder.buildCodingPrompt("Plan", "Task");

        assertTrue(result.contains("Java 21"));
        assertTrue(result.contains("Spring Boot 3.x"));
        assertTrue(result.contains("SOLID principles"));
        assertTrue(result.contains("Javadoc"));
    }

    @Test
    void should_buildReviewPrompt_when_calledWithDiff() {
        String result = promptBuilder.buildReviewPrompt("+ public void newMethod() { }");

        assertNotNull(result);
        assertTrue(result.contains("newMethod"));
        assertTrue(result.contains("code reviewer"));
        assertTrue(result.contains("Correctness"));
        assertTrue(result.contains("Security"));
    }

    @Test
    void should_containReviewCriteria_when_reviewPromptBuilt() {
        String result = promptBuilder.buildReviewPrompt("diff content");

        assertTrue(result.contains("Performance"));
        assertTrue(result.contains("Maintainability"));
        assertTrue(result.contains("Error Handling"));
        assertTrue(result.contains("CRITICAL"));
        assertTrue(result.contains("MAJOR"));
        assertTrue(result.contains("MINOR"));
        assertTrue(result.contains("SUGGESTION"));
    }

    @Test
    void should_buildQaPrompt_when_calledWithDiffAndDescription() {
        String result = promptBuilder.buildQaPrompt("+ test code changes", "Verify login feature");

        assertNotNull(result);
        assertTrue(result.contains("test code changes"));
        assertTrue(result.contains("Verify login feature"));
        assertTrue(result.contains("QA engineer"));
        assertTrue(result.contains("testing agent"));
    }

    @Test
    void should_containQaChecklist_when_qaPromptBuilt() {
        String result = promptBuilder.buildQaPrompt("diff", "description");

        assertTrue(result.contains("Requirements Coverage"));
        assertTrue(result.contains("Edge Cases"));
        assertTrue(result.contains("Regression Risk"));
        assertTrue(result.contains("Data Integrity"));
        assertTrue(result.contains("PASS"));
        assertTrue(result.contains("FAIL"));
    }

    @Test
    void should_handleEmptyInputs_when_buildingPrompts() {
        String planning = promptBuilder.buildPlanningPrompt("", "");
        String coding = promptBuilder.buildCodingPrompt("", "");
        String review = promptBuilder.buildReviewPrompt("");
        String qa = promptBuilder.buildQaPrompt("", "");

        assertNotNull(planning);
        assertNotNull(coding);
        assertNotNull(review);
        assertNotNull(qa);
    }

    @Test
    void should_buildPlanningPromptWithContext_allFields() {
        String result = promptBuilder.buildPlanningPromptWithContext(
                "Add OAuth login",
                "Implement OAuth2 login with Google and GitHub providers",
                "HIGH",
                List.of("feature", "auth", "backend"),
                "The project uses Spring Security with JWT tokens.");

        assertNotNull(result);
        assertTrue(result.contains("Add OAuth login"));
        assertTrue(result.contains("Implement OAuth2 login with Google and GitHub providers"));
        assertTrue(result.contains("**Priority:** HIGH"));
        assertTrue(result.contains("**Labels:** feature, auth, backend"));
        assertTrue(result.contains("## Codebase Context"));
        assertTrue(result.contains("The project uses Spring Security with JWT tokens."));
        assertTrue(result.contains("senior software architect"));
        assertTrue(result.contains("planning agent"));
        assertTrue(result.contains("Analyze the task requirements"));
        assertTrue(result.contains("testing strategy"));
    }

    @Test
    void should_buildPlanningPromptWithContext_nullOptionalFields() {
        String result = promptBuilder.buildPlanningPromptWithContext(
                "Fix bug", "Null pointer in service layer",
                null, null, null);

        assertNotNull(result);
        assertTrue(result.contains("Fix bug"));
        assertTrue(result.contains("Null pointer in service layer"));
        assertFalse(result.contains("**Priority:**"));
        assertFalse(result.contains("**Labels:**"));
        assertFalse(result.contains("## Codebase Context"));
        // Should still contain instructions
        assertTrue(result.contains("Analyze the task requirements"));
    }

    @Test
    void should_buildPlanningPromptWithContext_emptyLabels() {
        String result = promptBuilder.buildPlanningPromptWithContext(
                "Refactor service", "Extract common logic",
                "LOW", List.of(), "   ");

        assertNotNull(result);
        assertTrue(result.contains("Refactor service"));
        assertTrue(result.contains("**Priority:** LOW"));
        assertFalse(result.contains("**Labels:**"));
        // Blank codebase context should be treated as absent
        assertFalse(result.contains("## Codebase Context"));
    }
}
