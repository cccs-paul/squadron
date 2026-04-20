package com.squadron.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a ticketless task is created. The agent module listens on
 * {@code squadron.tasks.ticketless.created} to begin execution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TicketlessTaskCreatedEvent extends SquadronEvent {

    private UUID taskId;
    private String prompt;
    private String branchName;
    private boolean createBranch;
    /** PLAN or BUILD */
    private String agentMode;
    private UUID agentConfigId;
    private UUID projectId;

    {
        setEventType("TICKETLESS_TASK_CREATED");
    }
}
