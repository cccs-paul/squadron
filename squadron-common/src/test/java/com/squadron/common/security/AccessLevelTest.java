package com.squadron.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessLevelTest {

    @Test
    void should_haveNoneValue_when_enumDefined() {
        assertEquals("NONE", AccessLevel.NONE.name());
    }

    @Test
    void should_haveReadValue_when_enumDefined() {
        assertEquals("READ", AccessLevel.READ.name());
    }

    @Test
    void should_haveWriteValue_when_enumDefined() {
        assertEquals("WRITE", AccessLevel.WRITE.name());
    }

    @Test
    void should_haveAdminValue_when_enumDefined() {
        assertEquals("ADMIN", AccessLevel.ADMIN.name());
    }

    @Test
    void should_haveFourValues_when_enumDefined() {
        AccessLevel[] values = AccessLevel.values();

        assertEquals(4, values.length);
    }

    @Test
    void should_returnCorrectEnum_when_valueOfCalled() {
        assertEquals(AccessLevel.NONE, AccessLevel.valueOf("NONE"));
        assertEquals(AccessLevel.READ, AccessLevel.valueOf("READ"));
        assertEquals(AccessLevel.WRITE, AccessLevel.valueOf("WRITE"));
        assertEquals(AccessLevel.ADMIN, AccessLevel.valueOf("ADMIN"));
    }

    @Test
    void should_throwIllegalArgumentException_when_invalidValueOfCalled() {
        assertThrows(IllegalArgumentException.class, () -> AccessLevel.valueOf("UNKNOWN"));
    }

    @Test
    void should_haveCorrectOrdinalOrder_when_enumDefined() {
        assertTrue(AccessLevel.NONE.ordinal() < AccessLevel.READ.ordinal());
        assertTrue(AccessLevel.READ.ordinal() < AccessLevel.WRITE.ordinal());
        assertTrue(AccessLevel.WRITE.ordinal() < AccessLevel.ADMIN.ordinal());
    }
}
