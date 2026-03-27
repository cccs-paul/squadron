package com.squadron.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.SquadronConfigDto;
import com.squadron.agent.entity.SquadronConfig;
import com.squadron.agent.repository.SquadronConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SquadronConfigService {

    private static final Logger log = LoggerFactory.getLogger(SquadronConfigService.class);

    private final SquadronConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public SquadronConfigService(SquadronConfigRepository configRepository, ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates or updates a squadron configuration. Uses upsert logic based on
     * the tenantId/teamId/userId combination.
     */
    public SquadronConfig createOrUpdateConfig(SquadronConfigDto dto) {
        Optional<SquadronConfig> existing = configRepository
                .findByTenantIdAndTeamIdAndUserId(dto.getTenantId(), dto.getTeamId(), dto.getUserId());

        SquadronConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setName(dto.getName());
            config.setConfig(serializeConfig(dto.getConfig()));
            log.info("Updating squadron config {} for tenant {}", config.getId(), dto.getTenantId());
        } else {
            config = SquadronConfig.builder()
                    .tenantId(dto.getTenantId())
                    .teamId(dto.getTeamId())
                    .userId(dto.getUserId())
                    .name(dto.getName())
                    .config(serializeConfig(dto.getConfig()))
                    .build();
            log.info("Creating new squadron config for tenant {}", dto.getTenantId());
        }

        return configRepository.save(config);
    }

    /**
     * Resolves the effective configuration using hierarchical lookup:
     * user-specific -> team-level -> tenant-level.
     * Most specific configuration wins.
     */
    @Transactional(readOnly = true)
    public SquadronConfigDto resolveConfig(UUID tenantId, UUID teamId, UUID userId) {
        // Try user-specific config first
        if (userId != null && teamId != null) {
            Optional<SquadronConfig> userConfig = configRepository
                    .findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId);
            if (userConfig.isPresent()) {
                return toDto(userConfig.get());
            }
        }

        // Try team-level config
        if (teamId != null) {
            Optional<SquadronConfig> teamConfig = configRepository
                    .findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId);
            if (teamConfig.isPresent()) {
                return toDto(teamConfig.get());
            }
        }

        // Fall back to tenant-level config
        Optional<SquadronConfig> tenantConfig = configRepository
                .findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId);
        if (tenantConfig.isPresent()) {
            return toDto(tenantConfig.get());
        }

        log.debug("No squadron config found for tenant={}, team={}, user={}", tenantId, teamId, userId);
        return null;
    }

    /**
     * Extracts an AgentConfigDto for the specified agent type from the resolved config.
     */
    @Transactional(readOnly = true)
    public AgentConfigDto resolveAgentConfig(UUID tenantId, UUID teamId, UUID userId, String agentType) {
        SquadronConfigDto config = resolveConfig(tenantId, teamId, userId);
        if (config == null || config.getConfig() == null) {
            return AgentConfigDto.builder().build();
        }

        Map<String, Object> agentConfigs = config.getConfig();
        Object agentConfig = agentConfigs.get(agentType.toLowerCase());
        if (agentConfig instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> agentMap = (Map<String, Object>) agentConfig;
            return AgentConfigDto.builder()
                    .provider((String) agentMap.get("provider"))
                    .model((String) agentMap.get("model"))
                    .maxTokens(agentMap.get("maxTokens") instanceof Number n ? n.intValue() : null)
                    .temperature(agentMap.get("temperature") instanceof Number n ? n.doubleValue() : null)
                    .systemPromptOverride((String) agentMap.get("systemPromptOverride"))
                    .build();
        }

        return AgentConfigDto.builder().build();
    }

    @Transactional(readOnly = true)
    public SquadronConfig getConfig(UUID id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SquadronConfig", id));
    }

    @Transactional(readOnly = true)
    public List<SquadronConfig> listConfigsByTenant(UUID tenantId) {
        return configRepository.findByTenantId(tenantId);
    }

    public void deleteConfig(UUID id) {
        if (!configRepository.existsById(id)) {
            throw new ResourceNotFoundException("SquadronConfig", id);
        }
        configRepository.deleteById(id);
        log.info("Deleted squadron config {}", id);
    }

    private SquadronConfigDto toDto(SquadronConfig entity) {
        return SquadronConfigDto.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .teamId(entity.getTeamId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .config(deserializeConfig(entity.getConfig()))
                .build();
    }

    private String serializeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config != null ? config : Collections.emptyMap());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize config", e);
        }
    }

    private Map<String, Object> deserializeConfig(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize config JSON, returning empty map", e);
            return Collections.emptyMap();
        }
    }
}
