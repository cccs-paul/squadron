package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(TeamRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(TeamRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTenantIdMethod_when_checked() throws NoSuchMethodException {
        Method method = TeamRepository.class.getDeclaredMethod("findByTenantId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(TeamRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(TeamRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("Team"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
