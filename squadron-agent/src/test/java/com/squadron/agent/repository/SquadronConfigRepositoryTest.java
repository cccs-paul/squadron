package com.squadron.agent.repository;

import com.squadron.agent.entity.SquadronConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SquadronConfigRepositoryTest {

    @Test
    void should_extendJpaRepository_when_interfaceInspected() {
        assertTrue(JpaRepository.class.isAssignableFrom(SquadronConfigRepository.class));
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(SquadronConfigRepository.class.isInterface());
    }

    @Test
    void should_declareFindByTenantIdAndTeamIdAndUserId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = SquadronConfigRepository.class.getMethod(
                "findByTenantIdAndTeamIdAndUserId", UUID.class, UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndTeamIdAndUserIdIsNull_when_interfaceInspected() throws NoSuchMethodException {
        Method method = SquadronConfigRepository.class.getMethod(
                "findByTenantIdAndTeamIdAndUserIdIsNull", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantIdAndTeamIdIsNullAndUserIdIsNull_when_interfaceInspected() throws NoSuchMethodException {
        Method method = SquadronConfigRepository.class.getMethod(
                "findByTenantIdAndTeamIdIsNullAndUserIdIsNull", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareFindByTenantId_when_interfaceInspected() throws NoSuchMethodException {
        Method method = SquadronConfigRepository.class.getMethod("findByTenantId", UUID.class);
        assertEquals(List.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_haveFourCustomQueryMethods_when_interfaceInspected() {
        Method[] methods = SquadronConfigRepository.class.getDeclaredMethods();
        assertEquals(4, methods.length);
    }

    @Test
    void should_useUuidAsIdType_when_interfaceInspected() {
        // Verify the generic type parameters: JpaRepository<SquadronConfig, UUID>
        java.lang.reflect.Type[] genericInterfaces = SquadronConfigRepository.class.getGenericInterfaces();
        String genericSignature = genericInterfaces[0].getTypeName();
        assertTrue(genericSignature.contains("SquadronConfig"));
        assertTrue(genericSignature.contains("UUID"));
    }
}
