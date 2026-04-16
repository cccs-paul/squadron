package com.squadron.orchestrator.engine;

public enum TaskState {
    BACKLOG,
    PRIORITIZED,
    PLANNING,
    PROPOSE_CODE,
    IN_PROGRESS,
    REVIEW,
    QA,
    MERGE,
    DONE
}
