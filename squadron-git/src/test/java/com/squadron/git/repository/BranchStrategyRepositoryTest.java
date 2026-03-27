package com.squadron.git.repository;

import com.squadron.git.entity.BranchStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BranchStrategyRepositoryTest {

    @Test
    void should_beInterface() {
        assertTrue(BranchStrategyRepository.class.isInterface());
    }

    @Test
    void should_extendJpaRepository() {
        assertTrue(JpaRepository.class.isAssignableFrom(BranchStrategyRepository.class));
    }

    @Test
    void should_beAnnotatedWithRepository() {
        assertTrue(BranchStrategyRepository.class.isAnnotationPresent(Repository.class));
    }

    @Test
    void should_haveCorrectGenericTypes() {
        // Verify the interface extends JpaRepository<BranchStrategy, UUID>
        var genericInterfaces = BranchStrategyRepository.class.getGenericInterfaces();
        assertEquals(1, genericInterfaces.length);
        String genericType = genericInterfaces[0].getTypeName();
        assertTrue(genericType.contains(BranchStrategy.class.getName()));
        assertTrue(genericType.contains(UUID.class.getName()));
    }

    @Test
    void should_defineFindByTenantIdAndProjectIdMethod() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantIdAndProjectId", UUID.class, UUID.class);
        assertEquals(Optional.class, method.getReturnType());
    }

    @Test
    void should_defineFindByTenantIdAndProjectIdIsNullMethod() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantIdAndProjectIdIsNull", UUID.class);
        assertEquals(Optional.class, method.getReturnType());
    }

    @Test
    void should_defineFindByTenantIdMethod() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantId", UUID.class);
        assertEquals(List.class, method.getReturnType());
    }

    @Test
    void should_haveFindByTenantIdAndProjectId_withCorrectParameterTypes() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantIdAndProjectId", UUID.class, UUID.class);
        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals(2, paramTypes.length);
        assertEquals(UUID.class, paramTypes[0]);
        assertEquals(UUID.class, paramTypes[1]);
    }

    @Test
    void should_haveFindByTenantIdAndProjectIdIsNull_withSingleUuidParameter() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantIdAndProjectIdIsNull", UUID.class);
        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals(1, paramTypes.length);
        assertEquals(UUID.class, paramTypes[0]);
    }

    @Test
    void should_haveFindByTenantId_withSingleUuidParameter() throws NoSuchMethodException {
        Method method = BranchStrategyRepository.class.getMethod(
                "findByTenantId", UUID.class);
        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals(1, paramTypes.length);
        assertEquals(UUID.class, paramTypes[0]);
    }

    @Test
    void should_declareExactlyThreeCustomMethods() {
        // Count methods declared directly on this interface (excluding inherited ones)
        Method[] declaredMethods = BranchStrategyRepository.class.getDeclaredMethods();
        assertEquals(3, declaredMethods.length);
    }
}
