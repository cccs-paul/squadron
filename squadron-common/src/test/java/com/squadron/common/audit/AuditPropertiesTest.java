package com.squadron.common.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditPropertiesTest {

    @Test
    void should_haveCorrectDefaults() {
        AuditProperties properties = new AuditProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isPublishToNats());
        assertEquals("squadron.audit.events", properties.getNatsSubject());
        assertNotNull(properties.getExcludedActions());
        assertTrue(properties.getExcludedActions().isEmpty());
    }

    @Test
    void should_allowCustomValues() {
        AuditProperties properties = new AuditProperties();
        properties.setEnabled(false);
        properties.setPublishToNats(false);
        properties.setNatsSubject("custom.subject");
        properties.setExcludedActions(List.of("ACTION_A", "ACTION_B"));

        assertFalse(properties.isEnabled());
        assertFalse(properties.isPublishToNats());
        assertEquals("custom.subject", properties.getNatsSubject());
        assertEquals(2, properties.getExcludedActions().size());
        assertTrue(properties.getExcludedActions().contains("ACTION_A"));
        assertTrue(properties.getExcludedActions().contains("ACTION_B"));
    }

    @Test
    void should_returnNonNullString_when_callingToString() {
        AuditProperties properties = new AuditProperties();
        String str = properties.toString();
        assertNotNull(str);
        assertTrue(str.contains("enabled"));
        assertTrue(str.contains("publishToNats"));
    }

    @Test
    void should_supportEqualsAndHashCode() {
        AuditProperties p1 = new AuditProperties();
        AuditProperties p2 = new AuditProperties();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());

        p2.setEnabled(false);
        assertNotEquals(p1, p2);
    }
}
