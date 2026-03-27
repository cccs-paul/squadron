package com.squadron.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends SquadronEvent {

    private UUID userId;
    private String channel;
    private String subject;
    private String body;

    {
        setEventType("NOTIFICATION");
    }
}
