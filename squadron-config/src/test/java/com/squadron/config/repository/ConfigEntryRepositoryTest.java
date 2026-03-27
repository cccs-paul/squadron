package com.squadron.config.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for ConfigEntryRepository interface.
 * Since this is a Spring Data JPA repository interface, we verify the interface
 * exists and defines the expected query methods. Full integration tests would
 * require Testcontainers with a PostgreSQL database.
 */
class ConfigEntryRepositoryTest {

    @Test
    void should_beAValidInterface() {
        // Verify the interface is loadable and has expected method signatures
        assertNotNull(ConfigEntryRepository.class);
        assert ConfigEntryRepository.class.isInterface();
    }

    @Test
    void should_declareExpectedQueryMethods() throws NoSuchMethodException {
        // Verify all custom query methods are declared on the interface
        assertNotNull(ConfigEntryRepository.class.getMethod(
                "findByTenantIdAndConfigKey",
                java.util.UUID.class, String.class));

        assertNotNull(ConfigEntryRepository.class.getMethod(
                "findByTenantIdAndTeamIdAndUserIdAndConfigKey",
                java.util.UUID.class, java.util.UUID.class, java.util.UUID.class, String.class));

        assertNotNull(ConfigEntryRepository.class.getMethod(
                "findByTenantIdAndTeamIdIsNullAndUserIdIsNull",
                java.util.UUID.class));

        assertNotNull(ConfigEntryRepository.class.getMethod(
                "findByTenantIdAndTeamIdAndUserIdIsNull",
                java.util.UUID.class, java.util.UUID.class));

        assertNotNull(ConfigEntryRepository.class.getMethod(
                "findByTenantIdAndUserId",
                java.util.UUID.class, java.util.UUID.class));
    }
}
