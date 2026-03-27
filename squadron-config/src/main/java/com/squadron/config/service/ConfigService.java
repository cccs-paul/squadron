package com.squadron.config.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.config.dto.ConfigUpdateRequest;
import com.squadron.config.dto.ResolvedConfigDto;
import com.squadron.config.entity.ConfigAuditLog;
import com.squadron.config.entity.ConfigEntry;
import com.squadron.config.repository.ConfigAuditLogRepository;
import com.squadron.config.repository.ConfigEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private static final String NATS_SUBJECT_CONFIG_CHANGED = "squadron.config.changed";

    private final ConfigEntryRepository configEntryRepository;
    private final ConfigAuditLogRepository configAuditLogRepository;
    private final NatsEventPublisher natsEventPublisher;

    /**
     * Resolves a config value using the hierarchy: user -> team -> tenant -> default.
     * Returns the most specific config found.
     */
    @Transactional(readOnly = true)
    public ResolvedConfigDto resolveConfig(UUID tenantId, UUID teamId, UUID userId, String configKey) {
        // Level 1: User-level config (most specific)
        if (userId != null && teamId != null) {
            Optional<ConfigEntry> userConfig = configEntryRepository
                    .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, configKey);
            if (userConfig.isPresent()) {
                return toResolvedDto(userConfig.get(), "USER");
            }
        }

        // Level 1b: User-level config without team
        if (userId != null) {
            Optional<ConfigEntry> userConfig = configEntryRepository
                    .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, userId, configKey);
            if (userConfig.isPresent()) {
                return toResolvedDto(userConfig.get(), "USER");
            }
        }

        // Level 2: Team-level config
        if (teamId != null) {
            Optional<ConfigEntry> teamConfig = configEntryRepository
                    .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, null, configKey);
            if (teamConfig.isPresent()) {
                return toResolvedDto(teamConfig.get(), "TEAM");
            }
        }

        // Level 3: Tenant-level config
        Optional<ConfigEntry> tenantConfig = configEntryRepository
                .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, null, null, configKey);
        if (tenantConfig.isPresent()) {
            return toResolvedDto(tenantConfig.get(), "TENANT");
        }

        // Level 4: System default (tenant_id = all-zeros UUID)
        UUID systemDefaultTenantId = getSystemDefaultTenantId();
        Optional<ConfigEntry> defaultConfig = configEntryRepository
                .findByTenantIdAndTeamIdAndUserIdAndConfigKey(systemDefaultTenantId, null, null, configKey);
        if (defaultConfig.isPresent()) {
            return toResolvedDto(defaultConfig.get(), "DEFAULT");
        }

        throw new ResourceNotFoundException("ConfigEntry", configKey);
    }

    /**
     * Creates or updates a config entry. Logs to audit table and publishes NATS event.
     */
    public ConfigEntry setConfig(UUID tenantId, UUID teamId, UUID userId,
                                  ConfigUpdateRequest request) {
        Optional<ConfigEntry> existing = configEntryRepository
                .findByTenantIdAndTeamIdAndUserIdAndConfigKey(tenantId, teamId, userId, request.getConfigKey());

        ConfigEntry entry;
        String previousValue = null;

        if (existing.isPresent()) {
            entry = existing.get();
            previousValue = entry.getConfigValue();
            entry.setConfigValue(request.getConfigValue());
            entry.setDescription(request.getDescription());
            entry.setVersion(entry.getVersion() + 1);
        } else {
            entry = ConfigEntry.builder()
                    .tenantId(tenantId)
                    .teamId(teamId)
                    .userId(userId)
                    .configKey(request.getConfigKey())
                    .configValue(request.getConfigValue())
                    .description(request.getDescription())
                    .version(1)
                    .build();
        }

        ConfigEntry saved = configEntryRepository.save(entry);

        // Create audit log entry
        ConfigAuditLog auditLog = ConfigAuditLog.builder()
                .tenantId(tenantId)
                .configEntryId(saved.getId())
                .configKey(saved.getConfigKey())
                .previousValue(previousValue)
                .newValue(saved.getConfigValue())
                .build();
        configAuditLogRepository.save(auditLog);

        // Publish config change event to NATS
        publishConfigChangedEvent(saved);

        log.info("Config entry saved: key={}, tenantId={}, teamId={}, userId={}, version={}",
                saved.getConfigKey(), saved.getTenantId(), saved.getTeamId(),
                saved.getUserId(), saved.getVersion());

        return saved;
    }

    /**
     * Deletes a config entry by ID.
     */
    public void deleteConfig(UUID configId) {
        ConfigEntry entry = configEntryRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("ConfigEntry", configId));
        configEntryRepository.delete(entry);
        log.info("Config entry deleted: id={}, key={}", configId, entry.getConfigKey());
    }

    /**
     * Lists all configs at a given level.
     */
    @Transactional(readOnly = true)
    public List<ConfigEntry> listConfigs(UUID tenantId, UUID teamId, UUID userId) {
        if (userId != null) {
            return configEntryRepository.findByTenantIdAndUserId(tenantId, userId);
        }
        if (teamId != null) {
            return configEntryRepository.findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId);
        }
        return configEntryRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId);
    }

    /**
     * Gets the audit log for a specific config entry.
     */
    @Transactional(readOnly = true)
    public List<ConfigAuditLog> getAuditLog(UUID configEntryId) {
        return configAuditLogRepository.findByConfigEntryIdOrderByChangedAtDesc(configEntryId);
    }

    /**
     * System defaults use a well-known all-zeros UUID as the tenant ID.
     */
    private UUID getSystemDefaultTenantId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private ResolvedConfigDto toResolvedDto(ConfigEntry entry, String resolvedFrom) {
        return ResolvedConfigDto.builder()
                .configKey(entry.getConfigKey())
                .resolvedValue(entry.getConfigValue())
                .resolvedFrom(resolvedFrom)
                .tenantId(entry.getTenantId())
                .teamId(entry.getTeamId())
                .userId(entry.getUserId())
                .build();
    }

    private void publishConfigChangedEvent(ConfigEntry entry) {
        try {
            SquadronEvent event = new SquadronEvent();
            event.setEventType("CONFIG_CHANGED");
            event.setTenantId(entry.getTenantId());
            event.setSource("squadron-config");
            natsEventPublisher.publish(NATS_SUBJECT_CONFIG_CHANGED, event);
        } catch (Exception e) {
            log.warn("Failed to publish config change event for key={}: {}",
                    entry.getConfigKey(), e.getMessage());
        }
    }
}
