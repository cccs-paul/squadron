package com.squadron.review.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QAReportRepositoryTest {

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(QAReportRepository.class));
    }

    @Test
    void should_beAnInterface() {
        assertTrue(QAReportRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTaskIdMethod() throws NoSuchMethodException {
        Method method = QAReportRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveFindFirstByTaskIdOrderByCreatedAtDescMethod() throws NoSuchMethodException {
        Method method = QAReportRepository.class.getMethod("findFirstByTaskIdOrderByCreatedAtDesc", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveFindByTenantIdMethod() throws NoSuchMethodException {
        Method method = QAReportRepository.class.getMethod("findByTenantId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveCountByTaskIdAndVerdictMethod() throws NoSuchMethodException {
        Method method = QAReportRepository.class.getMethod("countByTaskIdAndVerdict", UUID.class, String.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }
}
