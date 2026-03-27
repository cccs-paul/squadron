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
public class TaskStateChangedEvent extends SquadronEvent {

    private UUID taskId;
    private String fromState;
    private String toState;
    private UUID triggeredBy;
    private String reason;

    {
        setEventType("TASK_STATE_CHANGED");
    }
}
