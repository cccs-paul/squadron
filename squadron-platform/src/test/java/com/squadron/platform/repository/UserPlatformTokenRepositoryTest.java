package com.squadron.platform.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the UserPlatformTokenRepository interface exists and defines the
 * expected custom query methods. Full integration testing with a real database
 * is done via Testcontainers integration tests.
 */
class UserPlatformTokenRepositoryTest {

    @Test
    void should_beLoadable() {
        // Verify the interface class is available at runtime
        assertNotNull(UserPlatformTokenRepository.class);
    }

    @Test
    void should_extendJpaRepository() {
        // Verify UserPlatformTokenRepository extends JpaRepository
        Class<?>[] interfaces = UserPlatformTokenRepository.class.getInterfaces();
        boolean extendsJpaRepository = false;
        for (Class<?> iface : interfaces) {
            if (iface.getSimpleName().equals("JpaRepository")) {
                extendsJpaRepository = true;
                break;
            }
        }
        assert extendsJpaRepository : "UserPlatformTokenRepository should extend JpaRepository";
    }

    @Test
    void should_haveCustomQueryMethods() throws NoSuchMethodException {
        // Verify the custom finder methods exist
        assertNotNull(UserPlatformTokenRepository.class.getMethod("findByUserIdAndConnectionId",
                java.util.UUID.class, java.util.UUID.class));
        assertNotNull(UserPlatformTokenRepository.class.getMethod("findByUserId", java.util.UUID.class));
    }
}
