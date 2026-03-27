package com.squadron.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskPlanRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(TaskPlanRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(TaskPlanRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTaskIdOrderByVersionDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TaskPlanRepository.class.getMethod(
                "findByTaskIdOrderByVersionDesc", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTaskIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TaskPlanRepository.class.getMethod(
                "findByTaskIdAndStatus", UUID.class, String.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindFirstByTaskIdOrderByVersionDesc_when_interfaceInspected() throws NoSuchMethodException {
        Method method = TaskPlanRepository.class.getMethod(
                "findFirstByTaskIdOrderByVersionDesc", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveThreeCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = TaskPlanRepository.class.getDeclaredMethods();
        assertEquals(3, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = TaskPlanRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("TaskPlan"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
