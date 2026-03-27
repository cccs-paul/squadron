package com.squadron.orchestrator.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(WorkflowDefinitionRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(WorkflowDefinitionRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdAndTeamIdAndActiveTrueMethod() throws NoSuchMethodException {
        var method = WorkflowDefinitionRepository.class.getMethod(
                "findByTenantIdAndTeamIdAndActiveTrue", UUID.class, UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(Optional.class));
    }

    @Test
    void should_haveFindByTenantIdAndTeamIdIsNullAndActiveTrueMethod() throws NoSuchMethodException {
        var method = WorkflowDefinitionRepository.class.getMethod(
                "findByTenantIdAndTeamIdIsNullAndActiveTrue", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(Optional.class));
    }

    @Test
    void should_haveFindByTenantIdMethod() throws NoSuchMethodException {
        var method = WorkflowDefinitionRepository.class.getMethod("findByTenantId", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }
}
