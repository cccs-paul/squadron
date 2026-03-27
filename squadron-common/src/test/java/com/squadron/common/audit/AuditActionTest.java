package com.squadron.common.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditActionTest {

    @Test
    void should_containAllExpectedValues() {
        AuditAction[] values = AuditAction.values();
        assertEquals(10, values.length);
    }

    @Test
    void should_returnCorrectName_when_callingName() {
        assertEquals("CREATE", AuditAction.CREATE.name());
        assertEquals("READ", AuditAction.READ.name());
        assertEquals("UPDATE", AuditAction.UPDATE.name());
        assertEquals("DELETE", AuditAction.DELETE.name());
        assertEquals("EXECUTE", AuditAction.EXECUTE.name());
        assertEquals("LOGIN", AuditAction.LOGIN.name());
        assertEquals("LOGOUT", AuditAction.LOGOUT.name());
        assertEquals("APPROVE", AuditAction.APPROVE.name());
        assertEquals("REJECT", AuditAction.REJECT.name());
        assertEquals("TRANSITION", AuditAction.TRANSITION.name());
    }

    @Test
    void should_returnCorrectEnum_when_callingValueOf() {
        assertEquals(AuditAction.CREATE, AuditAction.valueOf("CREATE"));
        assertEquals(AuditAction.READ, AuditAction.valueOf("READ"));
        assertEquals(AuditAction.UPDATE, AuditAction.valueOf("UPDATE"));
        assertEquals(AuditAction.DELETE, AuditAction.valueOf("DELETE"));
        assertEquals(AuditAction.EXECUTE, AuditAction.valueOf("EXECUTE"));
        assertEquals(AuditAction.TRANSITION, AuditAction.valueOf("TRANSITION"));
    }

    @Test
    void should_throwException_when_invalidValueOf() {
        assertThrows(IllegalArgumentException.class, () -> AuditAction.valueOf("INVALID"));
    }

    @Test
    void should_haveCorrectOrdinals() {
        assertEquals(0, AuditAction.CREATE.ordinal());
        assertEquals(1, AuditAction.READ.ordinal());
        assertEquals(2, AuditAction.UPDATE.ordinal());
        assertEquals(3, AuditAction.DELETE.ordinal());
        assertEquals(4, AuditAction.EXECUTE.ordinal());
        assertEquals(5, AuditAction.LOGIN.ordinal());
        assertEquals(6, AuditAction.LOGOUT.ordinal());
        assertEquals(7, AuditAction.APPROVE.ordinal());
        assertEquals(8, AuditAction.REJECT.ordinal());
        assertEquals(9, AuditAction.TRANSITION.ordinal());
    }
}
