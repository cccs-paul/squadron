package com.squadron.config.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for ConfigAuditLogRepository interface.
 * Since this is a Spring Data JPA repository interface, we verify the interface
 * exists and defines the expected query methods. Full integration tests would
 * require Testcontainers with a PostgreSQL database.
 */
class ConfigAuditLogRepositoryTest {

    @Test
    void should_beAValidInterface() {
        assertNotNull(ConfigAuditLogRepository.class);
        assert ConfigAuditLogRepository.class.isInterface();
    }

    @Test
    void should_declareExpectedQueryMethods() throws NoSuchMethodException {
        assertNotNull(ConfigAuditLogRepository.class.getMethod(
                "findByConfigEntryIdOrderByChangedAtDesc",
                java.util.UUID.class));

        assertNotNull(ConfigAuditLogRepository.class.getMethod(
                "findByTenantIdOrderByChangedAtDesc",
                java.util.UUID.class));
    }
}
