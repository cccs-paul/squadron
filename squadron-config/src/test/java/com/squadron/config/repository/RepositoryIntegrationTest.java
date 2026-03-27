package com.squadron.config.repository;

import com.squadron.config.entity.ConfigAuditLog;
import com.squadron.config.entity.ConfigEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.config.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.config.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_config_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConfigEntryRepository configEntryRepository;

    @Autowired
    private ConfigAuditLogRepository configAuditLogRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private ConfigEntry createConfigEntry(UUID tenantId, UUID teamId, UUID userId, String key, String value) {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .configKey(key)
                .configValue(value)
                .description("Test config for " + key)
                .build();
        return entityManager.persistFlushFind(entry);
    }

    private ConfigEntry createTenantLevelConfig(UUID tenantId, String key, String value) {
        return createConfigEntry(tenantId, null, null, key, value);
    }

    private ConfigAuditLog createAuditLog(UUID tenantId, UUID configEntryId, String configKey,
                                           String previousValue, String newValue) {
        ConfigAuditLog log = ConfigAuditLog.builder()
                .tenantId(tenantId)
                .configEntryId(configEntryId)
                .configKey(configKey)
                .previousValue(previousValue)
                .newValue(newValue)
                .changedBy(UUID.randomUUID())
                .build();
        return entityManager.persistFlushFind(log);
    }

    // =========================================================================
    // ConfigEntryRepository Tests
    // =========================================================================

    @Test
    void should_saveConfigEntry_when_validEntity() {
        ConfigEntry entry = ConfigEntry.builder()
                .tenantId(UUID.randomUUID())
                .configKey("ai.model")
                .configValue("{\"model\": \"gpt-4\"}")
                .description("AI model configuration")
                .build();

        ConfigEntry saved = configEntryRepository.save(entry);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConfigKey()).isEqualTo("ai.model");
        assertThat(saved.getConfigValue()).isEqualTo("{\"model\": \"gpt-4\"}");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getDescription()).isEqualTo("AI model configuration");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findById_when_configEntryExists() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "db.pool.size", "{\"value\": 10}");

        Optional<ConfigEntry> found = configEntryRepository.findById(entry.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getConfigKey()).isEqualTo("db.pool.size");
    }

    @Test
    void should_findByTenantIdAndConfigKey_when_entriesExist() {
        UUID tenantId = UUID.randomUUID();
        createTenantLevelConfig(tenantId, "ai.model", "{\"model\": \"gpt-4\"}");
        UUID teamId = UUID.randomUUID();
        createConfigEntry(tenantId, teamId, null, "ai.model", "{\"model\": \"gpt-3.5\"}");

        List<ConfigEntry> results = configEntryRepository.findByTenantIdAndConfigKey(tenantId, "ai.model");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> "ai.model".equals(e.getConfigKey()));
    }

    @Test
    void should_findByTenantIdAndTeamIdAndUserIdAndConfigKey_when_exactMatch() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createConfigEntry(tenantId, teamId, userId, "theme.color", "{\"value\": \"dark\"}");

        Optional<ConfigEntry> found = configEntryRepository
                .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, "theme.color");

        assertThat(found).isPresent();
        assertThat(found.get().getConfigValue()).isEqualTo("{\"value\": \"dark\"}");
    }

    @Test
    void should_returnEmpty_when_exactConfigKeyNotFound() {
        Optional<ConfigEntry> found = configEntryRepository
                .findByTenantIdAndTeamIdAndUserIdAndConfigKey(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "nonexistent.key");

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTenantIdAndTeamIdIsNullAndUserIdIsNull_when_tenantLevelConfigsExist() {
        UUID tenantId = UUID.randomUUID();
        createTenantLevelConfig(tenantId, "global.setting1", "{\"value\": true}");
        createTenantLevelConfig(tenantId, "global.setting2", "{\"value\": false}");
        // Team-level config should NOT be returned
        createConfigEntry(tenantId, UUID.randomUUID(), null, "team.setting", "{\"value\": 1}");

        List<ConfigEntry> results =
                configEntryRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getTeamId() == null && e.getUserId() == null);
    }

    @Test
    void should_findByTenantIdAndTeamIdAndUserIdIsNull_when_teamLevelConfigsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        createConfigEntry(tenantId, teamId, null, "team.setting1", "{\"value\": true}");
        createConfigEntry(tenantId, teamId, null, "team.setting2", "{\"value\": false}");
        // User-level config should NOT be returned
        createConfigEntry(tenantId, teamId, UUID.randomUUID(), "user.setting", "{\"value\": 1}");

        List<ConfigEntry> results =
                configEntryRepository.findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getTeamId().equals(teamId) && e.getUserId() == null);
    }

    @Test
    void should_findByTenantIdAndUserId_when_userConfigsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createConfigEntry(tenantId, null, userId, "user.pref1", "{\"value\": \"a\"}");
        createConfigEntry(tenantId, UUID.randomUUID(), userId, "user.pref2", "{\"value\": \"b\"}");
        // Different user's config should NOT be returned
        createConfigEntry(tenantId, null, UUID.randomUUID(), "user.pref1", "{\"value\": \"c\"}");

        List<ConfigEntry> results = configEntryRepository.findByTenantIdAndUserId(tenantId, userId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.getUserId().equals(userId));
    }

    @Test
    void should_deleteConfigEntry_when_exists() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "deletable.key", "{\"value\": true}");
        UUID entryId = entry.getId();

        configEntryRepository.deleteById(entryId);
        entityManager.flush();

        assertThat(configEntryRepository.findById(entryId)).isEmpty();
    }

    @Test
    void should_updateConfigEntry_when_fieldsChanged() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "updatable.key", "{\"value\": 1}");

        entry.setConfigValue("{\"value\": 2}");
        entry.setVersion(2);

        configEntryRepository.save(entry);
        entityManager.flush();
        entityManager.clear();

        ConfigEntry updated = configEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(updated.getConfigValue()).isEqualTo("{\"value\": 2}");
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    // =========================================================================
    // ConfigAuditLogRepository Tests
    // =========================================================================

    @Test
    void should_saveConfigAuditLog_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "audit.test.key", "{\"value\": 1}");

        ConfigAuditLog log = ConfigAuditLog.builder()
                .tenantId(tenantId)
                .configEntryId(entry.getId())
                .configKey("audit.test.key")
                .previousValue("{\"value\": 0}")
                .newValue("{\"value\": 1}")
                .changedBy(UUID.randomUUID())
                .build();

        ConfigAuditLog saved = configAuditLogRepository.save(log);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConfigKey()).isEqualTo("audit.test.key");
        assertThat(saved.getPreviousValue()).isEqualTo("{\"value\": 0}");
        assertThat(saved.getNewValue()).isEqualTo("{\"value\": 1}");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    void should_findByConfigEntryIdOrderByChangedAtDesc_when_logsExist() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "versioned.key", "{\"value\": 3}");

        createAuditLog(tenantId, entry.getId(), "versioned.key", null, "{\"value\": 1}");
        createAuditLog(tenantId, entry.getId(), "versioned.key", "{\"value\": 1}", "{\"value\": 2}");
        createAuditLog(tenantId, entry.getId(), "versioned.key", "{\"value\": 2}", "{\"value\": 3}");

        List<ConfigAuditLog> results =
                configAuditLogRepository.findByConfigEntryIdOrderByChangedAtDesc(entry.getId());

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(l -> l.getConfigEntryId().equals(entry.getId()));
        // Most recent first
        assertThat(results.get(0).getNewValue()).isEqualTo("{\"value\": 3}");
    }

    @Test
    void should_findByTenantIdOrderByChangedAtDesc_when_logsExist() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry1 = createTenantLevelConfig(tenantId, "key1", "{\"v\": 1}");
        ConfigEntry entry2 = createTenantLevelConfig(tenantId, "key2", "{\"v\": 2}");

        createAuditLog(tenantId, entry1.getId(), "key1", null, "{\"v\": 1}");
        createAuditLog(tenantId, entry2.getId(), "key2", null, "{\"v\": 2}");

        // Different tenant's log should NOT be returned
        UUID otherTenantId = UUID.randomUUID();
        ConfigEntry otherEntry = createTenantLevelConfig(otherTenantId, "key3", "{\"v\": 3}");
        createAuditLog(otherTenantId, otherEntry.getId(), "key3", null, "{\"v\": 3}");

        List<ConfigAuditLog> results =
                configAuditLogRepository.findByTenantIdOrderByChangedAtDesc(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(l -> l.getTenantId().equals(tenantId));
    }

    @Test
    void should_deleteAuditLog_when_exists() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "del.key", "{\"v\": 1}");
        ConfigAuditLog log = createAuditLog(tenantId, entry.getId(), "del.key", null, "{\"v\": 1}");
        UUID logId = log.getId();

        configAuditLogRepository.deleteById(logId);
        entityManager.flush();

        assertThat(configAuditLogRepository.findById(logId)).isEmpty();
    }

    @Test
    void should_cascadeDeleteAuditLogs_when_configEntryDeleted() {
        UUID tenantId = UUID.randomUUID();
        ConfigEntry entry = createTenantLevelConfig(tenantId, "cascade.key", "{\"v\": 1}");
        ConfigAuditLog log = createAuditLog(tenantId, entry.getId(), "cascade.key", null, "{\"v\": 1}");
        UUID logId = log.getId();

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM config_entries WHERE id = :id")
                .setParameter("id", entry.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(configAuditLogRepository.findById(logId)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noAuditLogsForEntry() {
        List<ConfigAuditLog> results =
                configAuditLogRepository.findByConfigEntryIdOrderByChangedAtDesc(UUID.randomUUID());

        assertThat(results).isEmpty();
    }
}
