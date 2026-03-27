package com.squadron.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging via AOP.
 * When a method annotated with {@code @Audited} is invoked, the
 * {@link AuditAspect} intercepts the call, extracts context information,
 * and publishes an {@link AuditEvent} through the {@link AuditService}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * The action name for the audit event (e.g. "TASK_STATE_CHANGED", "PR_CREATED").
     */
    String action();

    /**
     * The resource type being acted on (e.g. "TASK", "PULL_REQUEST", "CONFIG").
     */
    String resourceType();

    /**
     * The type of audit action (CREATE, READ, UPDATE, DELETE, EXECUTE, etc.).
     * Defaults to EXECUTE.
     */
    AuditAction auditAction() default AuditAction.EXECUTE;
}
