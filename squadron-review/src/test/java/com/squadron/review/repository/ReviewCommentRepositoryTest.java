package com.squadron.review.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewCommentRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(ReviewCommentRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(ReviewCommentRepository.class.isInterface());
    }

    @Test
    void should_declareFindByReviewId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewCommentRepository.class.getMethod("findByReviewId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByReviewIdAndSeverity_when_interfaceInspected() throws NoSuchMethodException {
        Method method = ReviewCommentRepository.class.getMethod(
                "findByReviewIdAndSeverity", UUID.class, String.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveTwoCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = ReviewCommentRepository.class.getDeclaredMethods();
        assertEquals(2, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        java.lang.reflect.Type[] genericInterfaces = ReviewCommentRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("ReviewComment"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
