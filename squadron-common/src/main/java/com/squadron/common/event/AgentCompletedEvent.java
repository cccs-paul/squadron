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
public class AgentCompletedEvent extends SquadronEvent {

    private UUID taskId;
    private UUID userId;
    private String agentType;
    private UUID conversationId;
    private boolean success;
    private long tokenCount;

    {
        setEventType("AGENT_COMPLETED");
    }
}
