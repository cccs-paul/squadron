package com.squadron.workspace.repository;

import com.squadron.workspace.entity.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(WorkspaceRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(WorkspaceRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTaskId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByUserId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceRepository.class.getMethod("findByUserId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceRepository.class.getMethod(
                "findByTenantIdAndStatus", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTaskIdAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceRepository.class.getMethod(
                "findByTaskIdAndStatus", UUID.class, String.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveFourCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = WorkspaceRepository.class.getDeclaredMethods();
        assertEquals(5, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = WorkspaceRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("Workspace"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
