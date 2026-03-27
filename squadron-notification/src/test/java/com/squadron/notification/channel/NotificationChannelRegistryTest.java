package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationChannelRegistryTest {

    @Test
    void should_registerAllChannels_when_constructed() {
        NotificationChannel channel1 = new TestChannel("IN_APP");
        NotificationChannel channel2 = new TestChannel("EMAIL");

        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of(channel1, channel2));

        assertNotNull(registry.getChannel("IN_APP"));
        assertNotNull(registry.getChannel("EMAIL"));
    }

    @Test
    void should_returnCorrectChannel_when_getChannelCalled() {
        NotificationChannel inApp = new TestChannel("IN_APP");
        NotificationChannel email = new TestChannel("EMAIL");

        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of(inApp, email));

        assertEquals("IN_APP", registry.getChannel("IN_APP").getChannelType());
        assertEquals("EMAIL", registry.getChannel("EMAIL").getChannelType());
    }

    @Test
    void should_throwException_when_unknownChannelType() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());

        assertThrows(IllegalArgumentException.class, () -> registry.getChannel("UNKNOWN"));
    }

    @Test
    void should_returnTrue_when_channelExists() {
        NotificationChannel channel = new TestChannel("SLACK");
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of(channel));

        assertTrue(registry.hasChannel("SLACK"));
    }

    @Test
    void should_returnFalse_when_channelDoesNotExist() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());

        assertFalse(registry.hasChannel("SLACK"));
    }

    private static class TestChannel implements NotificationChannel {
        private final String type;

        TestChannel(String type) {
            this.type = type;
        }

        @Override
        public String getChannelType() {
            return type;
        }

        @Override
        public void send(Notification notification, NotificationPreference preference) {
            // no-op for tests
        }
    }
}
