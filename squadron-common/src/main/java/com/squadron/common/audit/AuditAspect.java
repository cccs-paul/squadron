package com.squadron.common.audit;

import com.squadron.common.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * AOP aspect that intercepts methods annotated with {@link Audited} and
 * automatically publishes audit events through the {@link AuditService}.
 * <p>
 * Tenant and user information is extracted from {@link TenantContext} (thread-local).
 * Resource IDs are extracted from method parameters named "id", "resourceId", or
 * parameters of type {@link UUID}, or from the return value's {@code toString()}.
 * <p>
 * Audit failures are caught and logged but never propagated to callers.
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        // Always proceed with the business logic first
        Object result = joinPoint.proceed();

        // Audit after successful execution — failures must not break business logic
        try {
            UUID tenantId = TenantContext.getTenantId();
            UUID userId = TenantContext.getUserId();
            String username = TenantContext.getEmail();

            String resourceId = extractResourceId(joinPoint, result);

            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .username(username)
                    .action(audited.action())
                    .resourceType(audited.resourceType())
                    .resourceId(resourceId)
                    .auditAction(audited.auditAction())
                    .sourceService(resolveSourceService(joinPoint))
                    .build();

            auditService.logEvent(event);
        } catch (Exception e) {
            log.warn("Audit logging failed for method {}: {}",
                    joinPoint.getSignature().toShortString(), e.getMessage());
        }

        return result;
    }

    /**
     * Attempts to extract a resource ID from method parameters or return value.
     * Checks for parameters named "id" or "resourceId", or the first UUID parameter.
     * Falls back to the return value's toString() if available.
     */
    String extractResourceId(ProceedingJoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = joinPoint.getArgs();

            // Look for named parameters: id, resourceId
            for (int i = 0; i < parameters.length; i++) {
                String name = parameters[i].getName();
                if (("id".equals(name) || "resourceId".equals(name)) && args[i] != null) {
                    return args[i].toString();
                }
            }

            // Look for first UUID parameter
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getType() == UUID.class && args[i] != null) {
                    return args[i].toString();
                }
            }

            // Fall back to return value
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            log.debug("Failed to extract resource ID: {}", e.getMessage());
        }
        return null;
    }

    private String resolveSourceService(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName();
    }
}
