package com.squadron.identity.service;

import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.repository.AuthProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD service for auth provider configurations.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthProviderConfigService {

    private final AuthProviderConfigRepository authProviderConfigRepository;

    public AuthProviderConfig createConfig(UUID tenantId, String providerType, String name,
                                            String configJson, boolean enabled, int priority) {
        AuthProviderConfig config = AuthProviderConfig.builder()
                .tenantId(tenantId)
                .providerType(providerType)
                .name(name)
                .config(configJson != null ? configJson : "{}")
                .enabled(enabled)
                .priority(priority)
                .build();
        return authProviderConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public AuthProviderConfig getConfig(UUID id) {
        return authProviderConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuthProviderConfig", "id", id));
    }

    @Transactional(readOnly = true)
    public List<AuthProviderConfig> listConfigs(UUID tenantId) {
        return authProviderConfigRepository.findByTenantId(tenantId);
    }

    public AuthProviderConfig updateConfig(UUID id, String name, String providerType,
                                            String configJson, Boolean enabled, Integer priority) {
        AuthProviderConfig config = authProviderConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuthProviderConfig", "id", id));

        if (name != null) {
            config.setName(name);
        }
        if (providerType != null) {
            config.setProviderType(providerType);
        }
        if (configJson != null) {
            config.setConfig(configJson);
        }
        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (priority != null) {
            config.setPriority(priority);
        }

        return authProviderConfigRepository.save(config);
    }

    public void deleteConfig(UUID id) {
        if (!authProviderConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("AuthProviderConfig", "id", id);
        }
        authProviderConfigRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AuthProviderConfig> getEnabledConfigs(UUID tenantId) {
        return authProviderConfigRepository.findByTenantIdAndEnabledOrderByPriorityAsc(tenantId, true);
    }
}
