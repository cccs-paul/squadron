package com.squadron.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationChannelRegistry {

    private final Map<String, NotificationChannel> channelMap;

    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::getChannelType, Function.identity()));
        log.info("Registered notification channels: {}", channelMap.keySet());
    }

    public NotificationChannel getChannel(String type) {
        NotificationChannel channel = channelMap.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown notification channel type: " + type);
        }
        return channel;
    }

    public boolean hasChannel(String type) {
        return channelMap.containsKey(type);
    }
}
