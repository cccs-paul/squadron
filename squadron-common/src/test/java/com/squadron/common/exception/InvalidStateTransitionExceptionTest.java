package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvalidStateTransitionExceptionTest {

    @Test
    void should_setFromAndToState_when_created() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("OPEN", "DONE");

        assertEquals("OPEN", ex.getFromState());
        assertEquals("DONE", ex.getToState());
    }

    @Test
    void should_setErrorCodeToInvalidTransition_when_created() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("A", "B");

        assertEquals("INVALID_TRANSITION", ex.getErrorCode());
    }

    @Test
    void should_buildDescriptiveMessage_when_created() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("DRAFT", "CLOSED");

        assertEquals("Invalid state transition from DRAFT to CLOSED", ex.getMessage());
    }

    @Test
    void should_beSquadronException_when_created() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("A", "B");

        assertInstanceOf(SquadronException.class, ex);
    }

    @Test
    void should_includeStateNamesInMessage_when_differentStatesUsed() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("IN_PROGRESS", "OPEN");

        assertTrue(ex.getMessage().contains("IN_PROGRESS"));
        assertTrue(ex.getMessage().contains("OPEN"));
    }
}
