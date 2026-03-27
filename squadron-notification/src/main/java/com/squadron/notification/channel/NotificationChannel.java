package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;

public interface NotificationChannel {

    String getChannelType();

    void send(Notification notification, NotificationPreference preference);
}
