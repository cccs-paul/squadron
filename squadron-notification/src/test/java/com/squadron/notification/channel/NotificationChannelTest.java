package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationChannelTest {

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(NotificationChannel.class.isInterface());
    }

    @Test
    void should_declareGetChannelTypeMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationChannel.class.getMethod("getChannelType");
        assertEquals(String.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void should_declareSendMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = NotificationChannel.class.getMethod("send", Notification.class, NotificationPreference.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveTwoMethods_when_interfaceInspected() {
        Method[] methods = NotificationChannel.class.getDeclaredMethods();
        assertEquals(2, methods.length);
    }

    @Test
    void should_implementAllMethods_when_concreteImplementation() {
        NotificationChannel channel = new NotificationChannel() {
            @Override
            public String getChannelType() {
                return "EMAIL";
            }

            @Override
            public void send(Notification notification, NotificationPreference preference) {
                // no-op for test
            }
        };

        assertEquals("EMAIL", channel.getChannelType());
        assertDoesNotThrow(() -> channel.send(new Notification(), new NotificationPreference()));
    }

    @Test
    void should_acceptNotificationAndPreference_when_sendCalled() {
        final boolean[] sendCalled = {false};

        NotificationChannel channel = new NotificationChannel() {
            @Override
            public String getChannelType() {
                return "SLACK";
            }

            @Override
            public void send(Notification notification, NotificationPreference preference) {
                assertNotNull(notification);
                assertNotNull(preference);
                sendCalled[0] = true;
            }
        };

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setSubject("Test");
        notification.setBody("Test body");

        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(UUID.randomUUID());

        channel.send(notification, preference);
        assertTrue(sendCalled[0]);
    }
}
