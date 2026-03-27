package com.squadron.review.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ReviewRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ReviewRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTaskId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewRepository.class.getMethod("findByTaskId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTaskIdAndReviewerType_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewRepository.class.getMethod(
                "findByTaskIdAndReviewerType", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByReviewerId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewRepository.class.getMethod("findByReviewerId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCountByTaskIdAndReviewerTypeAndStatus_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewRepository.class.getMethod(
                "countByTaskIdAndReviewerTypeAndStatus", UUID.class, String.class, String.class);
        assertEquals(long.class, method.getReturnType());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    void should_haveFourCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ReviewRepository.class.getDeclaredMethods();
        assertEquals(4, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ReviewRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("Review"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
