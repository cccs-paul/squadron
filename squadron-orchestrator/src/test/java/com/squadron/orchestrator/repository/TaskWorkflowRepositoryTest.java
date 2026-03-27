package com.squadron.orchestrator.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskWorkflowRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(TaskWorkflowRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(TaskWorkflowRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTaskIdMethod() throws NoSuchMethodException {
        var method = TaskWorkflowRepository.class.getMethod("findByTaskId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(Optional.class));
    }

    @Test
    void should_haveFindByCurrentStateMethod() throws NoSuchMethodException {
        var method = TaskWorkflowRepository.class.getMethod("findByCurrentState", String.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }

    @Test
    void should_haveFindByTenantIdAndCurrentStateMethod() throws NoSuchMethodException {
        var method = TaskWorkflowRepository.class.getMethod(
                "findByTenantIdAndCurrentState", UUID.class, String.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }

    @Test
    void should_haveFindByTaskIdForUpdateMethod() throws NoSuchMethodException {
        var method = TaskWorkflowRepository.class.getMethod("findByTaskIdForUpdate", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(Optional.class));
    }
}
