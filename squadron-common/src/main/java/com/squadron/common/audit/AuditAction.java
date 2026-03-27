package com.squadron.common.audit;

/**
 * Enumeration of audit action types used to classify audit events.
 */
public enum AuditAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    EXECUTE,
    LOGIN,
    LOGOUT,
    APPROVE,
    REJECT,
    TRANSITION
}
