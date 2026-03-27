package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        NotificationEvent event = new NotificationEvent();

        assertEquals("NOTIFICATION", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID userId = UUID.randomUUID();

        NotificationEvent event = new NotificationEvent(userId, "email", "Task Completed", "Your task has been completed.");

        assertEquals("NOTIFICATION", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID userId = UUID.randomUUID();

        NotificationEvent event = new NotificationEvent(userId, "slack", "Review Ready", "Code review is ready.");

        assertEquals(userId, event.getUserId());
        assertEquals("slack", event.getChannel());
        assertEquals("Review Ready", event.getSubject());
        assertEquals("Code review is ready.", event.getBody());
    }

    @Test
    void should_haveNullFields_when_defaultConstructorUsed() {
        NotificationEvent event = new NotificationEvent();

        assertNull(event.getUserId());
        assertNull(event.getChannel());
        assertNull(event.getSubject());
        assertNull(event.getBody());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        NotificationEvent event = new NotificationEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        NotificationEvent event = new NotificationEvent();
        UUID userId = UUID.randomUUID();

        event.setUserId(userId);
        event.setChannel("webhook");
        event.setSubject("Alert");
        event.setBody("Something happened.");

        assertEquals(userId, event.getUserId());
        assertEquals("webhook", event.getChannel());
        assertEquals("Alert", event.getSubject());
        assertEquals("Something happened.", event.getBody());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        NotificationEvent event = new NotificationEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
