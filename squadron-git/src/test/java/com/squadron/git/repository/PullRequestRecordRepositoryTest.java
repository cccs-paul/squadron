package com.squadron.git.repository;

import com.squadron.git.entity.PullRequestRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestRecordRepositoryTest {

    @Test
    void should_beInterface() {
        assertTrue(PullRequestRecordRepository.class.isInterface());
    }

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(PullRequestRecordRepository.class));
    }

    @Test
    void should_defineFindByTaskIdMethod() throws NoSuchMethodException {
        Method method = PullRequestRecordRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
    }

    @Test
    void should_defineFindByTenantIdAndStatusMethod() throws NoSuchMethodException {
        Method method = PullRequestRecordRepository.class.getMethod("findByTenantIdAndStatus", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void should_beAnnotatedWithRepository() {
        assertTrue(PullRequestRecordRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }
}
