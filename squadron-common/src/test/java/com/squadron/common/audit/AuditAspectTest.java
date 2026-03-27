package com.squadron.common.audit;

import com.squadron.common.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(auditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_interceptAndAudit_when_methodAnnotatedWithAudited() throws Throwable {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .email("user@test.com")
                .build());

        Audited audited = createAuditedAnnotation("TASK_CREATED", "TASK", AuditAction.CREATE);

        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("createTask", UUID.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{UUID.randomUUID()});

        Object result = auditAspect.auditMethod(joinPoint, audited);

        assertEquals("result", result);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());

        AuditEvent event = captor.getValue();
        assertEquals(tenantId, event.getTenantId());
        assertEquals(userId, event.getUserId());
        assertEquals("user@test.com", event.getUsername());
        assertEquals("TASK_CREATED", event.getAction());
        assertEquals("TASK", event.getResourceType());
        assertEquals(AuditAction.CREATE, event.getAuditAction());
    }

    @Test
    void should_proceedEvenIfAuditFails() throws Throwable {
        TenantContext.setContext(TenantContext.builder().build());

        Audited audited = createAuditedAnnotation("TEST", "RESOURCE", AuditAction.EXECUTE);

        when(joinPoint.proceed()).thenReturn("business-result");
        doThrow(new RuntimeException("audit failure")).when(auditService).logEvent(any(AuditEvent.class));
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("simpleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        Object result = auditAspect.auditMethod(joinPoint, audited);

        assertEquals("business-result", result);
    }

    @Test
    void should_extractResourceId_fromUuidParameter() throws Throwable {
        TenantContext.setContext(TenantContext.builder().build());
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.READ);

        UUID resourceId = UUID.randomUUID();
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("createTask", UUID.class);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{resourceId});

        auditAspect.auditMethod(joinPoint, audited);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());
        assertEquals(resourceId.toString(), captor.getValue().getResourceId());
    }

    @Test
    void should_extractResourceId_fromReturnValue_when_noUuidParam() throws Throwable {
        TenantContext.setContext(TenantContext.builder().build());
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.CREATE);

        when(joinPoint.proceed()).thenReturn("returned-id-123");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("simpleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        auditAspect.auditMethod(joinPoint, audited);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());
        assertEquals("returned-id-123", captor.getValue().getResourceId());
    }

    @Test
    void should_handleNullReturnAndNoParams() throws Throwable {
        TenantContext.setContext(TenantContext.builder().build());
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.DELETE);

        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("simpleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        auditAspect.auditMethod(joinPoint, audited);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());
        assertNull(captor.getValue().getResourceId());
    }

    @Test
    void should_handleNullTenantContext() throws Throwable {
        TenantContext.clear();
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.EXECUTE);

        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(this);
        Method method = SampleService.class.getMethod("simpleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        Object result = auditAspect.auditMethod(joinPoint, audited);

        assertEquals("result", result);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());
        assertNull(captor.getValue().getTenantId());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    void should_setSourceService_fromTargetClassName() throws Throwable {
        TenantContext.setContext(TenantContext.builder().build());
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.EXECUTE);

        SampleService target = new SampleService();
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(target);
        Method method = SampleService.class.getMethod("simpleMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        auditAspect.auditMethod(joinPoint, audited);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).logEvent(captor.capture());
        assertEquals("SampleService", captor.getValue().getSourceService());
    }

    @Test
    void should_propagateBusinessException_when_joinPointThrows() throws Throwable {
        Audited audited = createAuditedAnnotation("TEST", "TASK", AuditAction.EXECUTE);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("business error"));

        assertThrows(RuntimeException.class, () -> auditAspect.auditMethod(joinPoint, audited));
        verify(auditService, never()).logEvent(any(AuditEvent.class));
    }

    // Helper to create @Audited annotation proxy
    private Audited createAuditedAnnotation(String action, String resourceType, AuditAction auditAction) {
        return new Audited() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Audited.class;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String resourceType() {
                return resourceType;
            }

            @Override
            public AuditAction auditAction() {
                return auditAction;
            }
        };
    }

    // Sample service class for reflective method access
    public static class SampleService {
        public String createTask(UUID id) {
            return "created";
        }

        public String simpleMethod() {
            return "simple";
        }
    }
}
