package com.squadron.orchestrator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskStateTest {

    @Test
    void should_haveEightStates() {
        assertEquals(8, TaskState.values().length);
    }

    @Test
    void should_containBacklogState() {
        assertNotNull(TaskState.BACKLOG);
        assertEquals("BACKLOG", TaskState.BACKLOG.name());
    }

    @Test
    void should_containPrioritizedState() {
        assertNotNull(TaskState.PRIORITIZED);
        assertEquals("PRIORITIZED", TaskState.PRIORITIZED.name());
    }

    @Test
    void should_containPlanningState() {
        assertNotNull(TaskState.PLANNING);
        assertEquals("PLANNING", TaskState.PLANNING.name());
    }

    @Test
    void should_containProposeCodeState() {
        assertNotNull(TaskState.PROPOSE_CODE);
        assertEquals("PROPOSE_CODE", TaskState.PROPOSE_CODE.name());
    }

    @Test
    void should_containReviewState() {
        assertNotNull(TaskState.REVIEW);
        assertEquals("REVIEW", TaskState.REVIEW.name());
    }

    @Test
    void should_containQaState() {
        assertNotNull(TaskState.QA);
        assertEquals("QA", TaskState.QA.name());
    }

    @Test
    void should_containMergeState() {
        assertNotNull(TaskState.MERGE);
        assertEquals("MERGE", TaskState.MERGE.name());
    }

    @Test
    void should_containDoneState() {
        assertNotNull(TaskState.DONE);
        assertEquals("DONE", TaskState.DONE.name());
    }

    @Test
    void should_resolveFromName_when_validName() {
        assertEquals(TaskState.BACKLOG, TaskState.valueOf("BACKLOG"));
        assertEquals(TaskState.PRIORITIZED, TaskState.valueOf("PRIORITIZED"));
        assertEquals(TaskState.PLANNING, TaskState.valueOf("PLANNING"));
        assertEquals(TaskState.PROPOSE_CODE, TaskState.valueOf("PROPOSE_CODE"));
        assertEquals(TaskState.REVIEW, TaskState.valueOf("REVIEW"));
        assertEquals(TaskState.QA, TaskState.valueOf("QA"));
        assertEquals(TaskState.MERGE, TaskState.valueOf("MERGE"));
        assertEquals(TaskState.DONE, TaskState.valueOf("DONE"));
    }

    @Test
    void should_throwException_when_invalidName() {
        assertThrows(IllegalArgumentException.class, () -> TaskState.valueOf("INVALID"));
    }

    @Test
    void should_haveCorrectOrdinals() {
        assertEquals(0, TaskState.BACKLOG.ordinal());
        assertEquals(1, TaskState.PRIORITIZED.ordinal());
        assertEquals(2, TaskState.PLANNING.ordinal());
        assertEquals(3, TaskState.PROPOSE_CODE.ordinal());
        assertEquals(4, TaskState.REVIEW.ordinal());
        assertEquals(5, TaskState.QA.ordinal());
        assertEquals(6, TaskState.MERGE.ordinal());
        assertEquals(7, TaskState.DONE.ordinal());
    }
}
