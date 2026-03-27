package com.squadron.orchestrator.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStateHistoryRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(TaskStateHistoryRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(TaskStateHistoryRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTaskWorkflowIdOrderByCreatedAtDescMethod() throws NoSuchMethodException {
        var method = TaskStateHistoryRepository.class.getMethod(
                "findByTaskWorkflowIdOrderByCreatedAtDesc", UUID.class);
        assertTrue(method.getReturnType().isAssignableFrom(List.class));
    }
}
