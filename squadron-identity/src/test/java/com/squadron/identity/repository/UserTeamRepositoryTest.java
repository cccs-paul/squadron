package com.squadron.identity.repository;

import com.squadron.identity.entity.UserTeamId;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTeamRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(UserTeamRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(UserTeamRepository.class.isInterface());
    }

    @Test
    void should_haveFindByTeamIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserTeamRepository.class.getDeclaredMethod("findByTeamId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByUserIdMethod_when_checked() throws NoSuchMethodException {
        Method method = UserTeamRepository.class.getDeclaredMethod("findByUserId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(UserTeamRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useUserTeamIdAsIdType_when_checked() {
        String genericInterface = Arrays.stream(UserTeamRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("UserTeam"));
        assertTrue(genericInterface.contains("UserTeamId"));
    }
}
