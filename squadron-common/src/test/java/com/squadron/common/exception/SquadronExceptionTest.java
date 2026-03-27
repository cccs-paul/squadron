package com.squadron.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SquadronExceptionTest {

    @Test
    void should_setMessageAndErrorCode_when_twoArgConstructorUsed() {
        SquadronException ex = new SquadronException("Something failed", "GENERAL_ERROR");

        assertEquals("Something failed", ex.getMessage());
        assertEquals("GENERAL_ERROR", ex.getErrorCode());
    }

    @Test
    void should_setMessageErrorCodeAndCause_when_threeArgConstructorUsed() {
        Throwable cause = new RuntimeException("root cause");
        SquadronException ex = new SquadronException("Wrapper error", "WRAPPED", cause);

        assertEquals("Wrapper error", ex.getMessage());
        assertEquals("WRAPPED", ex.getErrorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void should_beRuntimeException_when_created() {
        SquadronException ex = new SquadronException("test", "CODE");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void should_haveNullCause_when_twoArgConstructorUsed() {
        SquadronException ex = new SquadronException("test", "CODE");

        assertNull(ex.getCause());
    }

    @Test
    void should_preserveErrorCode_when_differentCodesUsed() {
        SquadronException ex1 = new SquadronException("a", "CODE_A");
        SquadronException ex2 = new SquadronException("b", "CODE_B");

        assertEquals("CODE_A", ex1.getErrorCode());
        assertEquals("CODE_B", ex2.getErrorCode());
    }
}
