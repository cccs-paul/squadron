package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SquadronEventTest {

    @Test
    void should_autoGenerateEventId_when_defaultConstructorUsed() {
        SquadronEvent event = new SquadronEvent();

        assertNotNull(event.getEventId());
    }

    @Test
    void should_autoGenerateTimestamp_when_defaultConstructorUsed() {
        Instant before = Instant.now();
        SquadronEvent event = new SquadronEvent();
        Instant after = Instant.now();

        assertNotNull(event.getTimestamp());
        assertFalse(event.getTimestamp().isBefore(before));
        assertFalse(event.getTimestamp().isAfter(after));
    }

    @Test
    void should_generateUniqueEventIds_when_multipleEventsCreated() {
        SquadronEvent event1 = new SquadronEvent();
        SquadronEvent event2 = new SquadronEvent();

        assertNotEquals(event1.getEventId(), event2.getEventId());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        SquadronEvent event = new SquadronEvent(eventId, "TEST_EVENT", tenantId, timestamp, "test-source");

        assertEquals(eventId, event.getEventId());
        assertEquals("TEST_EVENT", event.getEventType());
        assertEquals(tenantId, event.getTenantId());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals("test-source", event.getSource());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        SquadronEvent event = new SquadronEvent();
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        event.setEventId(eventId);
        event.setEventType("CUSTOM_EVENT");
        event.setTenantId(tenantId);
        event.setTimestamp(timestamp);
        event.setSource("custom-source");

        assertEquals(eventId, event.getEventId());
        assertEquals("CUSTOM_EVENT", event.getEventType());
        assertEquals(tenantId, event.getTenantId());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals("custom-source", event.getSource());
    }

    @Test
    void should_haveNullEventType_when_defaultConstructorUsed() {
        SquadronEvent event = new SquadronEvent();

        assertNull(event.getEventType());
    }

    @Test
    void should_haveNullTenantId_when_defaultConstructorUsed() {
        SquadronEvent event = new SquadronEvent();

        assertNull(event.getTenantId());
    }

    @Test
    void should_haveNullSource_when_defaultConstructorUsed() {
        SquadronEvent event = new SquadronEvent();

        assertNull(event.getSource());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        SquadronEvent event1 = new SquadronEvent(eventId, "TEST", tenantId, timestamp, "src");
        SquadronEvent event2 = new SquadronEvent(eventId, "TEST", tenantId, timestamp, "src");

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        SquadronEvent event1 = new SquadronEvent();
        SquadronEvent event2 = new SquadronEvent();

        assertNotEquals(event1, event2);
    }

    @Test
    void should_returnStringRepresentation_when_toStringCalled() {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("TEST");

        String str = event.toString();
        assertNotNull(str);
        assertTrue(str.contains("TEST"));
    }
}
