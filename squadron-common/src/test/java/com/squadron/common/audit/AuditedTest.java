package com.squadron.common.audit;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class AuditedTest {

    @Audited(action = "TEST_ACTION", resourceType = "TEST_RESOURCE")
    void annotatedMethodWithDefaults() {}

    @Audited(action = "CREATE_TASK", resourceType = "TASK", auditAction = AuditAction.CREATE)
    void annotatedMethodWithExplicitAuditAction() {}

    @Test
    void should_haveRuntimeRetention() {
        Retention retention = Audited.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void should_targetMethods() {
        Target target = Audited.class.getAnnotation(Target.class);
        assertNotNull(target);
        assertEquals(1, target.value().length);
        assertEquals(ElementType.METHOD, target.value()[0]);
    }

    @Test
    void should_beAnAnnotation() {
        assertTrue(Audited.class.isAnnotation());
    }

    @Test
    void should_haveActionElement() throws NoSuchMethodException {
        Method actionMethod = Audited.class.getDeclaredMethod("action");
        assertNotNull(actionMethod);
        assertEquals(String.class, actionMethod.getReturnType());
    }

    @Test
    void should_haveResourceTypeElement() throws NoSuchMethodException {
        Method resourceTypeMethod = Audited.class.getDeclaredMethod("resourceType");
        assertNotNull(resourceTypeMethod);
        assertEquals(String.class, resourceTypeMethod.getReturnType());
    }

    @Test
    void should_haveAuditActionElement() throws NoSuchMethodException {
        Method auditActionMethod = Audited.class.getDeclaredMethod("auditAction");
        assertNotNull(auditActionMethod);
        assertEquals(AuditAction.class, auditActionMethod.getReturnType());
    }

    @Test
    void should_haveDefaultAuditActionOfExecute() throws NoSuchMethodException {
        Method auditActionMethod = Audited.class.getDeclaredMethod("auditAction");
        Object defaultValue = auditActionMethod.getDefaultValue();
        assertEquals(AuditAction.EXECUTE, defaultValue);
    }

    @Test
    void should_readActionFromAnnotatedMethod() throws NoSuchMethodException {
        Method method = AuditedTest.class.getDeclaredMethod("annotatedMethodWithDefaults");
        Audited audited = method.getAnnotation(Audited.class);

        assertNotNull(audited);
        assertEquals("TEST_ACTION", audited.action());
        assertEquals("TEST_RESOURCE", audited.resourceType());
        assertEquals(AuditAction.EXECUTE, audited.auditAction());
    }

    @Test
    void should_readExplicitAuditAction() throws NoSuchMethodException {
        Method method = AuditedTest.class.getDeclaredMethod("annotatedMethodWithExplicitAuditAction");
        Audited audited = method.getAnnotation(Audited.class);

        assertNotNull(audited);
        assertEquals("CREATE_TASK", audited.action());
        assertEquals("TASK", audited.resourceType());
        assertEquals(AuditAction.CREATE, audited.auditAction());
    }

    @Test
    void should_notBePresentOnUnannotatedMethod() throws NoSuchMethodException {
        Method method = AuditedTest.class.getDeclaredMethod("should_notBePresentOnUnannotatedMethod");
        Audited audited = method.getAnnotation(Audited.class);

        assertNull(audited);
    }

    @Test
    void should_haveExactlyThreeElements() {
        Method[] methods = Audited.class.getDeclaredMethods();
        assertEquals(3, methods.length);
    }
}
