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
public class WorkspaceLifecycleEvent extends SquadronEvent {

    private UUID workspaceId;
    private UUID taskId;
    private String action;

    {
        setEventType("WORKSPACE_LIFECYCLE");
    }
}
