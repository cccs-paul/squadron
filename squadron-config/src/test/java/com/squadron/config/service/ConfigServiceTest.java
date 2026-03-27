package com.squadron.config.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.config.dto.ConfigUpdateRequest;
import com.squadron.config.dto.ResolvedConfigDto;
import com.squadron.config.entity.ConfigAuditLog;
import com.squadron.config.entity.ConfigEntry;
import com.squadron.config.repository.ConfigAuditLogRepository;
import com.squadron.config.repository.ConfigEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigEntryRepository configEntryRepository;

    @Mock
    private ConfigAuditLogRepository configAuditLogRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigService(configEntryRepository, configAuditLogRepository, natsEventPublisher);
    }

    // ============================================================
    // resolveConfig tests
    // ============================================================

    @Test
    void should_resolveUserLevelConfig_when_userAndTeamProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "max.retries";

        ConfigEntry userEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .configKey(key)
                .configValue("5")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, key))
                .thenReturn(Optional.of(userEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, teamId, userId, key);

        assertNotNull(result);
        assertEquals(key, result.getConfigKey());
        assertEquals("5", result.getResolvedValue());
        assertEquals("USER", result.getResolvedFrom());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(teamId, result.getTeamId());
        assertEquals(userId, result.getUserId());
    }

    @Test
    void should_resolveUserLevelConfigWithoutTeam_when_userProvidedButTeamNull() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "theme";

        ConfigEntry userEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(null)
                .userId(userId)
                .configKey(key)
                .configValue("dark")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, userId, key))
                .thenReturn(Optional.of(userEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, null, userId, key);

        assertEquals("USER", result.getResolvedFrom());
        assertEquals("dark", result.getResolvedValue());
    }

    @Test
    void should_resolveUserLevelWithoutTeam_when_userTeamConfigNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "theme";

        ConfigEntry userEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(null)
                .userId(userId)
                .configKey(key)
                .configValue("dark")
                .build();

        // user+team config not found
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, key))
                .thenReturn(Optional.empty());
        // user without team found
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, userId, key))
                .thenReturn(Optional.of(userEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, teamId, userId, key);

        assertEquals("USER", result.getResolvedFrom());
        assertEquals("dark", result.getResolvedValue());
    }

    @Test
    void should_resolveTeamLevelConfig_when_noUserConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "max.retries";

        ConfigEntry teamEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(null)
                .configKey(key)
                .configValue("3")
                .build();

        // user-level with team = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, key))
                .thenReturn(Optional.empty());
        // user-level without team = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, userId, key))
                .thenReturn(Optional.empty());
        // team-level = found
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, null, key))
                .thenReturn(Optional.of(teamEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, teamId, userId, key);

        assertEquals("TEAM", result.getResolvedFrom());
        assertEquals("3", result.getResolvedValue());
    }

    @Test
    void should_resolveTeamLevelConfig_when_noUserIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String key = "max.retries";

        ConfigEntry teamEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(null)
                .configKey(key)
                .configValue("3")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, null, key))
                .thenReturn(Optional.of(teamEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, teamId, null, key);

        assertEquals("TEAM", result.getResolvedFrom());
        assertEquals("3", result.getResolvedValue());
    }

    @Test
    void should_resolveTenantLevelConfig_when_noTeamOrUserConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "max.retries";

        ConfigEntry tenantEntry = ConfigEntry.builder()
                .tenantId(tenantId)
                .teamId(null)
                .userId(null)
                .configKey(key)
                .configValue("2")
                .build();

        // user-level with team = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, key))
                .thenReturn(Optional.empty());
        // user-level without team = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, userId, key))
                .thenReturn(Optional.empty());
        // team-level = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, null, key))
                .thenReturn(Optional.empty());
        // tenant-level = found
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, key))
                .thenReturn(Optional.of(tenantEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, teamId, userId, key);

        assertEquals("TENANT", result.getResolvedFrom());
        assertEquals("2", result.getResolvedValue());
    }

    @Test
    void should_resolveDefaultConfig_when_noOtherConfigExists() {
        UUID tenantId = UUID.randomUUID();
        String key = "max.retries";
        UUID systemDefault = UUID.fromString("00000000-0000-0000-0000-000000000000");

        ConfigEntry defaultEntry = ConfigEntry.builder()
                .tenantId(systemDefault)
                .teamId(null)
                .userId(null)
                .configKey(key)
                .configValue("1")
                .build();

        // tenant-level = empty
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, key))
                .thenReturn(Optional.empty());
        // system default = found
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(systemDefault, null, null, key))
                .thenReturn(Optional.of(defaultEntry));

        ResolvedConfigDto result = configService.resolveConfig(tenantId, null, null, key);

        assertEquals("DEFAULT", result.getResolvedFrom());
        assertEquals("1", result.getResolvedValue());
    }

    @Test
    void should_throwNotFound_when_noConfigExistsAtAnyLevel() {
        UUID tenantId = UUID.randomUUID();
        String key = "nonexistent.key";
        UUID systemDefault = UUID.fromString("00000000-0000-0000-0000-000000000000");

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, key))
                .thenReturn(Optional.empty());
        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(systemDefault, null, null, key))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> configService.resolveConfig(tenantId, null, null, key));
    }

    // ============================================================
    // setConfig tests
    // ============================================================

    @Test
    void should_createNewConfig_when_noExistingEntry() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("feature.flag")
                .configValue("true")
                .description("Enable feature")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, "feature.flag"))
                .thenReturn(Optional.empty());

        ConfigEntry savedEntry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .configKey("feature.flag")
                .configValue("true")
                .description("Enable feature")
                .version(1)
                .build();

        when(configEntryRepository.save(any(ConfigEntry.class))).thenReturn(savedEntry);
        when(configAuditLogRepository.save(any(ConfigAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        ConfigEntry result = configService.setConfig(tenantId, teamId, userId, request);

        assertNotNull(result);
        assertEquals("feature.flag", result.getConfigKey());
        assertEquals("true", result.getConfigValue());
        assertEquals(1, result.getVersion());

        // Verify audit log was created with null previous value
        ArgumentCaptor<ConfigAuditLog> auditCaptor = ArgumentCaptor.forClass(ConfigAuditLog.class);
        verify(configAuditLogRepository).save(auditCaptor.capture());
        ConfigAuditLog auditLog = auditCaptor.getValue();
        assertNull(auditLog.getPreviousValue());
        assertEquals("true", auditLog.getNewValue());
        assertEquals("feature.flag", auditLog.getConfigKey());

        verify(natsEventPublisher).publish(anyString(), any());
    }

    @Test
    void should_updateExistingConfig_when_entryAlreadyExists() {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        ConfigEntry existingEntry = ConfigEntry.builder()
                .id(configId)
                .tenantId(tenantId)
                .configKey("feature.flag")
                .configValue("false")
                .description("Old description")
                .version(1)
                .build();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("feature.flag")
                .configValue("true")
                .description("Updated description")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, "feature.flag"))
                .thenReturn(Optional.of(existingEntry));
        when(configEntryRepository.save(any(ConfigEntry.class))).thenReturn(existingEntry);
        when(configAuditLogRepository.save(any(ConfigAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        ConfigEntry result = configService.setConfig(tenantId, null, null, request);

        assertEquals("true", result.getConfigValue());
        assertEquals("Updated description", result.getDescription());
        assertEquals(2, result.getVersion());

        // Verify audit log was created with old previous value
        ArgumentCaptor<ConfigAuditLog> auditCaptor = ArgumentCaptor.forClass(ConfigAuditLog.class);
        verify(configAuditLogRepository).save(auditCaptor.capture());
        ConfigAuditLog auditLog = auditCaptor.getValue();
        assertEquals("false", auditLog.getPreviousValue());
        assertEquals("true", auditLog.getNewValue());
    }

    @Test
    void should_createConfigWithNullTeamAndUser_when_tenantLevelConfig() {
        UUID tenantId = UUID.randomUUID();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("timeout")
                .configValue("30")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, "timeout"))
                .thenReturn(Optional.empty());

        ConfigEntry savedEntry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configKey("timeout")
                .configValue("30")
                .version(1)
                .build();

        when(configEntryRepository.save(any(ConfigEntry.class))).thenReturn(savedEntry);
        when(configAuditLogRepository.save(any(ConfigAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        ConfigEntry result = configService.setConfig(tenantId, null, null, request);

        assertEquals("timeout", result.getConfigKey());
        assertEquals("30", result.getConfigValue());
    }

    @Test
    void should_stillSaveConfig_when_natsPublishFails() {
        UUID tenantId = UUID.randomUUID();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key1")
                .configValue("val1")
                .build();

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, "key1"))
                .thenReturn(Optional.empty());

        ConfigEntry savedEntry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configKey("key1")
                .configValue("val1")
                .version(1)
                .build();

        when(configEntryRepository.save(any(ConfigEntry.class))).thenReturn(savedEntry);
        when(configAuditLogRepository.save(any(ConfigAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("NATS connection refused")).when(natsEventPublisher).publish(anyString(), any());

        // Should not throw even though NATS fails
        ConfigEntry result = configService.setConfig(tenantId, null, null, request);

        assertNotNull(result);
        assertEquals("key1", result.getConfigKey());
        verify(configEntryRepository).save(any(ConfigEntry.class));
    }

    // ============================================================
    // deleteConfig tests
    // ============================================================

    @Test
    void should_deleteConfig_when_exists() {
        UUID configId = UUID.randomUUID();
        ConfigEntry entry = ConfigEntry.builder()
                .id(configId)
                .tenantId(UUID.randomUUID())
                .configKey("some.key")
                .configValue("some.value")
                .build();

        when(configEntryRepository.findById(configId)).thenReturn(Optional.of(entry));

        configService.deleteConfig(configId);

        verify(configEntryRepository).delete(entry);
    }

    @Test
    void should_throwNotFound_when_deleteConfigMissing() {
        UUID configId = UUID.randomUUID();
        when(configEntryRepository.findById(configId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> configService.deleteConfig(configId));
        verify(configEntryRepository, never()).delete(any());
    }

    // ============================================================
    // listConfigs tests
    // ============================================================

    @Test
    void should_listUserConfigs_when_userIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<ConfigEntry> entries = List.of(
                ConfigEntry.builder().tenantId(tenantId).userId(userId).configKey("k1").configValue("v1").build(),
                ConfigEntry.builder().tenantId(tenantId).userId(userId).configKey("k2").configValue("v2").build()
        );

        when(configEntryRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(entries);

        List<ConfigEntry> result = configService.listConfigs(tenantId, null, userId);

        assertEquals(2, result.size());
        verify(configEntryRepository).findByTenantIdAndUserId(tenantId, userId);
    }

    @Test
    void should_listTeamConfigs_when_teamIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        List<ConfigEntry> entries = List.of(
                ConfigEntry.builder().tenantId(tenantId).teamId(teamId).configKey("k1").configValue("v1").build()
        );

        when(configEntryRepository.findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId)).thenReturn(entries);

        List<ConfigEntry> result = configService.listConfigs(tenantId, teamId, null);

        assertEquals(1, result.size());
        verify(configEntryRepository).findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId);
    }

    @Test
    void should_listTenantConfigs_when_noTeamOrUserProvided() {
        UUID tenantId = UUID.randomUUID();

        List<ConfigEntry> entries = List.of(
                ConfigEntry.builder().tenantId(tenantId).configKey("k1").configValue("v1").build()
        );

        when(configEntryRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId)).thenReturn(entries);

        List<ConfigEntry> result = configService.listConfigs(tenantId, null, null);

        assertEquals(1, result.size());
        verify(configEntryRepository).findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId);
    }

    @Test
    void should_returnEmptyList_when_noConfigsExist() {
        UUID tenantId = UUID.randomUUID();

        when(configEntryRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId)).thenReturn(List.of());

        List<ConfigEntry> result = configService.listConfigs(tenantId, null, null);

        assertEquals(0, result.size());
    }

    @Test
    void should_preferUserIdOverTeamId_when_bothProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<ConfigEntry> entries = List.of(
                ConfigEntry.builder().tenantId(tenantId).userId(userId).configKey("k1").configValue("v1").build()
        );

        when(configEntryRepository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(entries);

        List<ConfigEntry> result = configService.listConfigs(tenantId, teamId, userId);

        assertEquals(1, result.size());
        // Should use userId path, not teamId path
        verify(configEntryRepository).findByTenantIdAndUserId(tenantId, userId);
        verify(configEntryRepository, never()).findByTenantIdAndTeamIdAndUserIdIsNull(any(), any());
    }

    // ============================================================
    // getAuditLog tests
    // ============================================================

    @Test
    void should_returnAuditLogs_when_configEntryExists() {
        UUID configEntryId = UUID.randomUUID();

        List<ConfigAuditLog> logs = List.of(
                ConfigAuditLog.builder().configEntryId(configEntryId).configKey("k1")
                        .previousValue("old").newValue("new").build(),
                ConfigAuditLog.builder().configEntryId(configEntryId).configKey("k1")
                        .previousValue(null).newValue("old").build()
        );

        when(configAuditLogRepository.findByConfigEntryIdOrderByChangedAtDesc(configEntryId)).thenReturn(logs);

        List<ConfigAuditLog> result = configService.getAuditLog(configEntryId);

        assertEquals(2, result.size());
        verify(configAuditLogRepository).findByConfigEntryIdOrderByChangedAtDesc(configEntryId);
    }

    @Test
    void should_returnEmptyList_when_noAuditLogsExist() {
        UUID configEntryId = UUID.randomUUID();

        when(configAuditLogRepository.findByConfigEntryIdOrderByChangedAtDesc(configEntryId)).thenReturn(List.of());

        List<ConfigAuditLog> result = configService.getAuditLog(configEntryId);

        assertEquals(0, result.size());
    }
}
