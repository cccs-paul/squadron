package com.squadron.agent.service;

import com.squadron.agent.dto.AgentTestConfigDto;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.repository.AgentTestConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AgentTestConfigServiceTest {

    @Mock
    private AgentTestConfigRepository repository;

    private AgentTestConfigService service;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new AgentTestConfigService(repository);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void should_createDefaultConfig_when_noneExists() {
        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.empty());
        when(repository.save(any(AgentTestConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentTestConfig result = service.getOrCreateConfig(tenantId, userId);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals(userId, result.getUserId());
        assertEquals("ollama", result.getGeneratorProvider());
        assertEquals("gemma4:e2b", result.getGeneratorModel());
        verify(repository).save(any(AgentTestConfig.class));
    }

    @Test
    void should_returnExistingConfig_when_alreadyExists() {
        AgentTestConfig existing = AgentTestConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .build();

        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(existing));

        AgentTestConfig result = service.getOrCreateConfig(tenantId, userId);

        assertEquals("openai", result.getGeneratorProvider());
        assertEquals("gpt-4o", result.getGeneratorModel());
        verify(repository, never()).save(any());
    }

    @Test
    void should_updateConfig_when_validDto() {
        AgentTestConfig existing = AgentTestConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .build();

        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(existing));
        when(repository.save(any(AgentTestConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentTestConfigDto dto = AgentTestConfigDto.builder()
                .generatorProvider("anthropic")
                .generatorModel("claude-opus-4")
                .generatorHostingType("PLATFORM")
                .generatorBaseUrl("https://api.anthropic.com")
                .generatorApiKey("sk-secret-key")
                .build();

        AgentTestConfig result = service.updateConfig(tenantId, userId, dto);

        assertEquals("anthropic", result.getGeneratorProvider());
        assertEquals("claude-opus-4", result.getGeneratorModel());
        assertEquals("PLATFORM", result.getGeneratorHostingType());
        assertEquals("https://api.anthropic.com", result.getGeneratorBaseUrl());
        assertEquals("sk-secret-key", result.getGeneratorApiKey());
        verify(repository).save(any(AgentTestConfig.class));
    }

    @Test
    void should_maskApiKey_when_convertingToDto() {
        AgentTestConfig config = AgentTestConfig.builder()
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .generatorHostingType("PLATFORM")
                .generatorBaseUrl("https://api.openai.com")
                .generatorApiKey("sk-very-secret-key-12345")
                .build();

        AgentTestConfigDto dto = service.toDto(config);

        assertEquals("openai", dto.getGeneratorProvider());
        assertEquals("gpt-4o", dto.getGeneratorModel());
        assertEquals("PLATFORM", dto.getGeneratorHostingType());
        assertEquals("https://api.openai.com", dto.getGeneratorBaseUrl());
        assertEquals("********", dto.getGeneratorApiKey());
    }

    @Test
    void should_handleNullApiKey_when_convertingToDto() {
        AgentTestConfig config = AgentTestConfig.builder()
                .generatorProvider("ollama")
                .generatorModel("gemma4:e2b")
                .generatorHostingType("SELF_HOSTED")
                .generatorApiKey(null)
                .build();

        AgentTestConfigDto dto = service.toDto(config);

        assertNull(dto.getGeneratorApiKey());
    }

    @Test
    void should_setDefaultHostingType_when_dtoHasNull() {
        AgentTestConfig existing = AgentTestConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .build();

        when(repository.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(existing));
        when(repository.save(any(AgentTestConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentTestConfigDto dto = AgentTestConfigDto.builder()
                .generatorProvider("ollama")
                .generatorModel("gemma4:e2b")
                .generatorHostingType(null)
                .build();

        AgentTestConfig result = service.updateConfig(tenantId, userId, dto);

        assertEquals("SELF_HOSTED", result.getGeneratorHostingType());
    }
}
