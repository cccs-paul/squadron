package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.SquadronConfigDto;
import com.squadron.agent.entity.SquadronConfig;
import com.squadron.agent.repository.SquadronConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SquadronConfigServiceTest {

    @Mock
    private SquadronConfigRepository configRepository;

    private SquadronConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configService = new SquadronConfigService(configRepository, objectMapper);
    }

    @Test
    void should_createConfig_when_noExistingConfig() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> configMap = Map.of("coding", Map.of("provider", "openai"));

        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("New Config")
                .config(configMap)
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, null, null))
                .thenReturn(Optional.empty());

        SquadronConfig savedConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("New Config")
                .config("{\"coding\":{\"provider\":\"openai\"}}")
                .build();

        when(configRepository.save(any(SquadronConfig.class))).thenReturn(savedConfig);

        SquadronConfig result = configService.createOrUpdateConfig(dto);

        assertNotNull(result);
        assertEquals("New Config", result.getName());
        verify(configRepository).save(any(SquadronConfig.class));
    }

    @Test
    void should_updateConfig_when_existingConfigFound() {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        SquadronConfig existing = SquadronConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .name("Old Name")
                .config("{}")
                .build();

        Map<String, Object> newConfigMap = Map.of("coding", Map.of("provider", "openai"));

        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("Updated Name")
                .config(newConfigMap)
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, null, null))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SquadronConfig.class))).thenReturn(existing);

        SquadronConfig result = configService.createOrUpdateConfig(dto);

        assertNotNull(result);
        assertEquals("Updated Name", existing.getName());
        verify(configRepository).save(existing);
    }

    @Test
    void should_resolveUserConfig_when_userConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SquadronConfig userConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("User Config")
                .config("{\"coding\":{\"provider\":\"openai\"}}")
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId))
                .thenReturn(Optional.of(userConfig));

        SquadronConfigDto result = configService.resolveConfig(tenantId, teamId, userId);

        assertNotNull(result);
        assertEquals("User Config", result.getName());
        assertEquals(userId, result.getUserId());
    }

    @Test
    void should_resolveTeamConfig_when_noUserConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SquadronConfig teamConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Team Config")
                .config("{\"coding\":{\"provider\":\"openai\"}}")
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId))
                .thenReturn(Optional.empty());
        when(configRepository.findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId))
                .thenReturn(Optional.of(teamConfig));

        SquadronConfigDto result = configService.resolveConfig(tenantId, teamId, userId);

        assertNotNull(result);
        assertEquals("Team Config", result.getName());
    }

    @Test
    void should_resolveTenantConfig_when_noUserOrTeamConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SquadronConfig tenantConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Tenant Config")
                .config("{\"coding\":{\"provider\":\"openai\"}}")
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId))
                .thenReturn(Optional.empty());
        when(configRepository.findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId))
                .thenReturn(Optional.empty());
        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        SquadronConfigDto result = configService.resolveConfig(tenantId, teamId, userId);

        assertNotNull(result);
        assertEquals("Tenant Config", result.getName());
    }

    @Test
    void should_returnNull_when_noConfigFound() {
        UUID tenantId = UUID.randomUUID();

        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        SquadronConfigDto result = configService.resolveConfig(tenantId, null, null);

        assertNull(result);
    }

    @Test
    void should_resolveAgentConfig_when_agentTypeExistsInConfig() {
        UUID tenantId = UUID.randomUUID();

        Map<String, Object> codingConfig = new HashMap<>();
        codingConfig.put("provider", "openai-compatible");
        codingConfig.put("model", "gpt-4");
        codingConfig.put("maxTokens", 4096);
        codingConfig.put("temperature", 0.7);
        codingConfig.put("systemPromptOverride", "Custom prompt");

        String jsonConfig;
        try {
            jsonConfig = objectMapper.writeValueAsString(Map.of("coding", codingConfig));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SquadronConfig config = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Config")
                .config(jsonConfig)
                .build();

        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.of(config));

        AgentConfigDto result = configService.resolveAgentConfig(tenantId, null, null, "CODING");

        assertNotNull(result);
        assertEquals("openai-compatible", result.getProvider());
        assertEquals("gpt-4", result.getModel());
        assertEquals(4096, result.getMaxTokens());
        assertEquals(0.7, result.getTemperature());
        assertEquals("Custom prompt", result.getSystemPromptOverride());
    }

    @Test
    void should_returnEmptyAgentConfig_when_agentTypeNotInConfig() {
        UUID tenantId = UUID.randomUUID();

        SquadronConfig config = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Config")
                .config("{\"coding\":{\"provider\":\"openai\"}}")
                .build();

        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.of(config));

        AgentConfigDto result = configService.resolveAgentConfig(tenantId, null, null, "REVIEW");

        assertNotNull(result);
        assertNull(result.getProvider());
    }

    @Test
    void should_returnEmptyAgentConfig_when_noConfigResolved() {
        UUID tenantId = UUID.randomUUID();

        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        AgentConfigDto result = configService.resolveAgentConfig(tenantId, null, null, "CODING");

        assertNotNull(result);
        assertNull(result.getProvider());
    }

    @Test
    void should_getConfig_when_exists() {
        UUID configId = UUID.randomUUID();
        SquadronConfig config = SquadronConfig.builder()
                .id(configId)
                .tenantId(UUID.randomUUID())
                .name("Test Config")
                .config("{}")
                .build();

        when(configRepository.findById(configId)).thenReturn(Optional.of(config));

        SquadronConfig result = configService.getConfig(configId);

        assertNotNull(result);
        assertEquals(configId, result.getId());
    }

    @Test
    void should_throwNotFound_when_configDoesNotExist() {
        UUID configId = UUID.randomUUID();
        when(configRepository.findById(configId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> configService.getConfig(configId));
    }

    @Test
    void should_listConfigsByTenant_when_called() {
        UUID tenantId = UUID.randomUUID();
        SquadronConfig c1 = SquadronConfig.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .name("Config 1").config("{}").build();
        SquadronConfig c2 = SquadronConfig.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .name("Config 2").config("{}").build();

        when(configRepository.findByTenantId(tenantId)).thenReturn(List.of(c1, c2));

        List<SquadronConfig> result = configService.listConfigsByTenant(tenantId);

        assertEquals(2, result.size());
    }

    @Test
    void should_deleteConfig_when_exists() {
        UUID configId = UUID.randomUUID();
        when(configRepository.existsById(configId)).thenReturn(true);

        configService.deleteConfig(configId);

        verify(configRepository).deleteById(configId);
    }

    @Test
    void should_throwNotFound_when_deletingNonexistentConfig() {
        UUID configId = UUID.randomUUID();
        when(configRepository.existsById(configId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> configService.deleteConfig(configId));
    }

    @Test
    void should_handleNullConfigMap_when_serializing() {
        UUID tenantId = UUID.randomUUID();
        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("Null Config")
                .config(null)
                .build();

        when(configRepository.findByTenantIdAndTeamIdAndUserId(tenantId, null, null))
                .thenReturn(Optional.empty());

        SquadronConfig savedConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Null Config")
                .config("{}")
                .build();

        when(configRepository.save(any(SquadronConfig.class))).thenReturn(savedConfig);

        SquadronConfig result = configService.createOrUpdateConfig(dto);

        assertNotNull(result);
    }

    @Test
    void should_skipTeamLookup_when_teamIdIsNull() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SquadronConfig tenantConfig = SquadronConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Tenant Config")
                .config("{}")
                .build();

        when(configRepository.findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        // teamId is null, userId provided but no teamId means skip user lookup
        SquadronConfigDto result = configService.resolveConfig(tenantId, null, userId);

        assertNotNull(result);
        assertEquals("Tenant Config", result.getName());
    }
}
