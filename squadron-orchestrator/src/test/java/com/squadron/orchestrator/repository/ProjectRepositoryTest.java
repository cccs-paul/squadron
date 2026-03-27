package com.squadron.orchestrator.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(ProjectRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(ProjectRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdMethod() throws NoSuchMethodException {
        var method = ProjectRepository.class.getMethod("findByTenantId", java.util.UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(java.util.List.class));
    }

    @Test
    void should_haveFindByTeamIdMethod() throws NoSuchMethodException {
        var method = ProjectRepository.class.getMethod("findByTeamId", java.util.UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(java.util.List.class));
    }
}
