package com.squadron.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SquadronEvent {

    private UUID eventId = UUID.randomUUID();
    private String eventType;
    private UUID tenantId;
    private Instant timestamp = Instant.now();
    private String source;
}
