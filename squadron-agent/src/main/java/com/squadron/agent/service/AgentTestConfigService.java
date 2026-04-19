package com.squadron.agent.service;

import com.squadron.agent.dto.AgentTestConfigDto;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.repository.AgentTestConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages per-user test generator configuration (which model to use for
 * generating fake plans, code, and reviews during agent testing).
 */
@Service
@Transactional
public class AgentTestConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentTestConfigService.class);

    private final AgentTestConfigRepository repository;

    public AgentTestConfigService(AgentTestConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the user's test generator config, creating a default one if none exists.
     */
    public AgentTestConfig getOrCreateConfig(UUID tenantId, UUID userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> {
                    log.info("Creating default test generator config for user {} in tenant {}", userId, tenantId);
                    AgentTestConfig config = AgentTestConfig.builder()
                            .tenantId(tenantId)
                            .userId(userId)
                            .build();
                    return repository.save(config);
                });
    }

    /**
     * Updates the user's test generator configuration.
     */
    public AgentTestConfig updateConfig(UUID tenantId, UUID userId, AgentTestConfigDto dto) {
        AgentTestConfig config = getOrCreateConfig(tenantId, userId);
        config.setGeneratorProvider(dto.getGeneratorProvider());
        config.setGeneratorModel(dto.getGeneratorModel());
        config.setGeneratorHostingType(dto.getGeneratorHostingType() != null
                ? dto.getGeneratorHostingType() : "SELF_HOSTED");
        config.setGeneratorBaseUrl(dto.getGeneratorBaseUrl());
        config.setGeneratorApiKey(dto.getGeneratorApiKey());

        AgentTestConfig saved = repository.save(config);
        log.info("Updated test generator config for user {} in tenant {}: provider={}, model={}",
                userId, tenantId, saved.getGeneratorProvider(), saved.getGeneratorModel());
        return saved;
    }

    /**
     * Converts an entity to a DTO (hides the API key).
     */
    public AgentTestConfigDto toDto(AgentTestConfig config) {
        return AgentTestConfigDto.builder()
                .generatorProvider(config.getGeneratorProvider())
                .generatorModel(config.getGeneratorModel())
                .generatorHostingType(config.getGeneratorHostingType())
                .generatorBaseUrl(config.getGeneratorBaseUrl())
                // API key is masked for security
                .generatorApiKey(config.getGeneratorApiKey() != null ? "********" : null)
                .build();
    }
}
