package com.squadron.identity.repository;

import com.squadron.identity.entity.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantRepositoryTest {

    @Test
    void should_extendJpaRepository_when_checked() {
        assertTrue(JpaRepository.class.isAssignableFrom(TenantRepository.class));
    }

    @Test
    void should_beInterface_when_checked() {
        assertTrue(TenantRepository.class.isInterface());
    }

    @Test
    void should_haveFindBySlugMethod_when_checked() throws NoSuchMethodException {
        Method method = TenantRepository.class.getDeclaredMethod("findBySlug", String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveFindByStatusMethod_when_checked() throws NoSuchMethodException {
        Method method = TenantRepository.class.getDeclaredMethod("findByStatus", String.class);

        assertNotNull(method);
    }

    @Test
    void should_haveRepositoryAnnotation_when_checked() {
        assertTrue(TenantRepository.class.isAnnotationPresent(
                org.springframework.stereotype.Repository.class));
    }

    @Test
    void should_useUuidAsIdType_when_checked() {
        // Verify the interface signature uses UUID as the ID type
        List<String> typeParams = Arrays.stream(TenantRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .toList();
        assertTrue(typeParams.stream().anyMatch(t -> t.contains("UUID")));
    }

    @Test
    void should_useTenantAsEntityType_when_checked() {
        List<String> typeParams = Arrays.stream(TenantRepository.class.getGenericInterfaces())
                .map(Object::toString)
                .toList();
        assertTrue(typeParams.stream().anyMatch(t -> t.contains("Tenant")));
    }
}
