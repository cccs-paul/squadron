package com.squadron.orchestrator.engine;

/**
 * Lifecycle states for ticketless tasks (tasks created directly from the UI
 * without an external ticket).
 */
public enum TicketlessStatus {
    /** Task created, awaiting agent execution */
    CREATED,
    /** Planning agent is working */
    PLANNING,
    /** Coding/build agent is working */
    BUILDING,
    /** Agent completed successfully */
    COMPLETED,
    /** Agent execution failed */
    FAILED
}
