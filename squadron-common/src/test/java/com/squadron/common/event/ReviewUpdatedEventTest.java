package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewUpdatedEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();

        assertEquals("REVIEW_UPDATED", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ReviewUpdatedEvent event = new ReviewUpdatedEvent(reviewId, taskId, "AI", "APPROVED");

        assertEquals("REVIEW_UPDATED", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ReviewUpdatedEvent event = new ReviewUpdatedEvent(reviewId, taskId, "HUMAN", "CHANGES_REQUESTED");

        assertEquals(reviewId, event.getReviewId());
        assertEquals(taskId, event.getTaskId());
        assertEquals("HUMAN", event.getReviewerType());
        assertEquals("CHANGES_REQUESTED", event.getStatus());
    }

    @Test
    void should_haveNullFields_when_defaultConstructorUsed() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();

        assertNull(event.getReviewId());
        assertNull(event.getTaskId());
        assertNull(event.getReviewerType());
        assertNull(event.getStatus());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        event.setReviewId(reviewId);
        event.setTaskId(taskId);
        event.setReviewerType("AI");
        event.setStatus("APPROVED");

        assertEquals(reviewId, event.getReviewId());
        assertEquals(taskId, event.getTaskId());
        assertEquals("AI", event.getReviewerType());
        assertEquals("APPROVED", event.getStatus());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
