package com.squadron.agent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopResultTest {

    @Test
    void should_createSuccessResult_when_usingAllArgsConstructor() {
        AgentLoopResult result = new AgentLoopResult(true, 5, "Completed all tasks successfully");

        assertTrue(result.isSuccess());
        assertEquals(5, result.getIterations());
        assertEquals("Completed all tasks successfully", result.getSummary());
    }

    @Test
    void should_createFailureResult_when_usingAllArgsConstructor() {
        AgentLoopResult result = new AgentLoopResult(false, 10, "Max iterations reached");

        assertFalse(result.isSuccess());
        assertEquals(10, result.getIterations());
        assertEquals("Max iterations reached", result.getSummary());
    }

    @Test
    void should_createResult_when_usingNoArgsConstructor() {
        AgentLoopResult result = new AgentLoopResult();

        assertFalse(result.isSuccess());
        assertEquals(0, result.getIterations());
        assertNull(result.getSummary());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AgentLoopResult result = new AgentLoopResult();

        result.setSuccess(true);
        result.setIterations(3);
        result.setSummary("Generated login endpoint and tests");

        assertTrue(result.isSuccess());
        assertEquals(3, result.getIterations());
        assertEquals("Generated login endpoint and tests", result.getSummary());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        AgentLoopResult r1 = new AgentLoopResult(true, 7, "Done");
        AgentLoopResult r2 = new AgentLoopResult(true, 7, "Done");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentSuccess() {
        AgentLoopResult r1 = new AgentLoopResult(true, 5, "Summary");
        AgentLoopResult r2 = new AgentLoopResult(false, 5, "Summary");

        assertNotEquals(r1, r2);
    }

    @Test
    void should_notBeEqual_when_differentIterations() {
        AgentLoopResult r1 = new AgentLoopResult(true, 3, "Summary");
        AgentLoopResult r2 = new AgentLoopResult(true, 8, "Summary");

        assertNotEquals(r1, r2);
    }

    @Test
    void should_notBeEqual_when_differentSummary() {
        AgentLoopResult r1 = new AgentLoopResult(true, 5, "Summary A");
        AgentLoopResult r2 = new AgentLoopResult(true, 5, "Summary B");

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        AgentLoopResult result = new AgentLoopResult(true, 4, "All files written");
        String toString = result.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("All files written"));
        assertTrue(toString.contains("4"));
    }

    @Test
    void should_handleZeroIterations_when_agentCompletesImmediately() {
        AgentLoopResult result = new AgentLoopResult(true, 0, "No work needed");

        assertTrue(result.isSuccess());
        assertEquals(0, result.getIterations());
        assertEquals("No work needed", result.getSummary());
    }

    @Test
    void should_handleNullSummary_when_notProvided() {
        AgentLoopResult result = new AgentLoopResult(false, 1, null);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertNull(result.getSummary());
    }
}
