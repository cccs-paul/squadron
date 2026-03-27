package com.squadron.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityGroupMemberRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(SecurityGroupMemberRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(SecurityGroupMemberRepository.class.isInterface());
    }

    @Test
    void should_haveFindByGroupIdMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupMemberRepository.class.getDeclaredMethod(
                "findByGroupId", UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByMemberTypeAndMemberIdMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupMemberRepository.class.getDeclaredMethod(
                "findByMemberTypeAndMemberId", String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveDeleteByGroupIdAndMemberTypeAndMemberIdMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupMemberRepository.class.getDeclaredMethod(
                "deleteByGroupIdAndMemberTypeAndMemberId", UUID.class, String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveExistsByGroupIdAndMemberTypeAndMemberIdMethod_when_checked() throws NoSuchMethodException {
        Method method = SecurityGroupMemberRepository.class.getDeclaredMethod(
                "existsByGroupIdAndMemberTypeAndMemberId", UUID.class, String.class, UUID.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(SecurityGroupMemberRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useCorrectGenericTypes_when_checked() {
        String genericInterface = Arrays.stream(SecurityGroupMemberRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .findFirst()
                .orElse("");
        assertTrue(genericInterface.contains("SecurityGroupMember"));
        assertTrue(genericInterface.contains("UUID"));
    }
}
