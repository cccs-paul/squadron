package com.squadron.git.repository;

import com.squadron.git.entity.GitOperation;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitOperationRepositoryTest {

    @Test
    void should_beInterface() {
        assertTrue(GitOperationRepository.class.isInterface());
    }

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(GitOperationRepository.class));
    }

    @Test
    void should_defineFindByTaskIdMethod() throws NoSuchMethodException {
        Method method = GitOperationRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void should_defineFindByWorkspaceIdMethod() throws NoSuchMethodException {
        Method method = GitOperationRepository.class.getMethod("findByWorkspaceId", UUID.class);
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void should_beAnnotatedWithRepository() {
        assertTrue(GitOperationRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }
}
