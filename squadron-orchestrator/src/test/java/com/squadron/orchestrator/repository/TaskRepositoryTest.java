package com.squadron.orchestrator.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(TaskRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(TaskRepository.class.isInterface());
    }

    @Test
    void should_haveFindByProjectIdMethod() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("findByProjectId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }

    @Test
    void should_haveFindByTenantIdMethod() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("findByTenantId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }

    @Test
    void should_haveFindByTeamIdMethod() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("findByTeamId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }

    @Test
    void should_haveFindByExternalIdMethod() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("findByExternalId", String.class);
        assertTrue(method.getReturnType().isAssignableFrom(Optional.class));
    }

    @Test
    void should_haveFindByAssigneeIdMethod() throws NoSuchMethodException {
        var method = TaskRepository.class.getMethod("findByAssigneeId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }
}
