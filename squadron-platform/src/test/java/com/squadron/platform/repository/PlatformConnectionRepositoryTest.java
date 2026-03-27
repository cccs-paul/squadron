package com.squadron.platform.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the PlatformConnectionRepository interface exists and defines the
 * expected custom query methods. Full integration testing with a real database
 * is done via Testcontainers integration tests.
 */
class PlatformConnectionRepositoryTest {

    @Test
    void should_beLoadable() {
        // Verify the interface class is available at runtime
        assertNotNull(PlatformConnectionRepository.class);
    }

    @Test
    void should_extendJpaRepository() {
        // Verify PlatformConnectionRepository extends JpaRepository
        Class<?>[] interfaces = PlatformConnectionRepository.class.getInterfaces();
        boolean extendsJpaRepository = false;
        for (Class<?> iface : interfaces) {
            if (iface.getSimpleName().equals("JpaRepository")) {
                extendsJpaRepository = true;
                break;
            }
        }
        assert extendsJpaRepository : "PlatformConnectionRepository should extend JpaRepository";
    }

    @Test
    void should_haveCustomQueryMethods() throws NoSuchMethodException {
        // Verify the custom finder methods exist
        assertNotNull(PlatformConnectionRepository.class.getMethod("findByTenantId", java.util.UUID.class));
        assertNotNull(PlatformConnectionRepository.class.getMethod("findByTenantIdAndPlatformType",
                java.util.UUID.class, String.class));
        assertNotNull(PlatformConnectionRepository.class.getMethod("findByTenantIdAndStatus",
                java.util.UUID.class, String.class));
    }
}
