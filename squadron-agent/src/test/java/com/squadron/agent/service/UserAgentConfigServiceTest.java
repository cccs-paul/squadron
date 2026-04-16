package com.squadron.agent.service;

import com.squadron.agent.dto.UserAgentConfigDto;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAgentConfigServiceTest {

    @Mock
    private UserAgentConfigRepository repository;

    private UserAgentConfigService service;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new UserAgentConfigService(repository, 8);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ============================================================
    // getUserSquadron
    // ============================================================

    @Test
    void should_returnExistingAgents_when_userHasSquadron() {
        List<UserAgentConfig> agents = List.of(
                buildAgent("Sol", "GENERAL", 0),
                buildAgent("Titan", "GENERAL", 1)
        );
        when(repository.findByTenantIdAndUserIdOrderByDisplayOrderAsc(tenantId, userId))
                .thenReturn(agents);

        List<UserAgentConfig> result = service.getUserSquadron(tenantId, userId);

        assertEquals(2, result.size());
        assertEquals("Sol", result.get(0).getAgentName());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void should_seedDefaults_when_userHasNoAgents() {
        when(repository.findByTenantIdAndUserIdOrderByDisplayOrderAsc(tenantId, userId))
                .thenReturn(Collections.emptyList());

        // saveAll returns whatever was passed
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserAgentConfig> result = service.getUserSquadron(tenantId, userId);

        assertEquals(8, result.size());
        // Verify first agent: Sol with github-copilot/claude-sonnet-4
        assertEquals("Sol", result.get(0).getAgentName());
        assertEquals("GENERAL", result.get(0).getAgentType());
        assertEquals("github-copilot", result.get(0).getProvider());
        assertEquals("claude-sonnet-4", result.get(0).getModel());
        assertEquals("PLATFORM", result.get(0).getHostingType());
        assertEquals("Claude Sonnet 4 via GitHub Copilot", result.get(0).getDescription());

        // Verify second agent: Titan with github-copilot/gpt-4o
        assertEquals("Titan", result.get(1).getAgentName());
        assertEquals("GENERAL", result.get(1).getAgentType());
        assertEquals("github-copilot", result.get(1).getProvider());
        assertEquals("gpt-4o", result.get(1).getModel());
        assertEquals("GPT-4o via GitHub Copilot", result.get(1).getDescription());

        // Verify last agent: Nebula with ollama (self-hosted)
        assertEquals("Nebula", result.get(7).getAgentName());
        assertEquals("GENERAL", result.get(7).getAgentType());
        assertEquals("ollama", result.get(7).getProvider());
        assertEquals("llama3.3", result.get(7).getModel());
        assertEquals("SELF_HOSTED", result.get(7).getHostingType());
        assertEquals("Llama 3.3 (local)", result.get(7).getDescription());

        verify(repository).saveAll(anyList());
    }

    @Test
    void should_respectMaxLimit_when_seedingDefaults() {
        // Create service with max=3
        UserAgentConfigService limitedService = new UserAgentConfigService(repository, 3);

        when(repository.findByTenantIdAndUserIdOrderByDisplayOrderAsc(tenantId, userId))
                .thenReturn(Collections.emptyList());
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserAgentConfig> result = limitedService.getUserSquadron(tenantId, userId);

        assertEquals(3, result.size());
    }

    // ============================================================
    // addAgent
    // ============================================================

    @Test
    void should_addAgent_when_validAndUnderLimit() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(5L);
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "New Agent")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> {
            UserAgentConfig a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("New Agent")
                .agentType("GENERAL")
                .displayOrder(5)
                .provider("openai")
                .model("gpt-4o")
                .hostingType("PLATFORM")
                .description("GPT-4o via OpenAI")
                .maxTokens(4096)
                .temperature(0.3)
                .enabled(true)
                .build();

        UserAgentConfig result = service.addAgent(tenantId, userId, dto);

        assertNotNull(result);
        assertEquals("New Agent", result.getAgentName());
        assertEquals("GENERAL", result.getAgentType());
        assertEquals("openai", result.getProvider());
        assertEquals("gpt-4o", result.getModel());
        assertEquals("PLATFORM", result.getHostingType());
        assertEquals("GPT-4o via OpenAI", result.getDescription());
        verify(repository).save(any(UserAgentConfig.class));
    }

    @Test
    void should_autoGenerateDescription_when_noDescriptionProvided() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(2L);
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Auto Desc")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Auto Desc")
                .agentType("GENERAL")
                .provider("anthropic")
                .model("claude-opus-4")
                .hostingType("PLATFORM")
                // description not set — should be auto-generated
                .enabled(true)
                .build();

        UserAgentConfig result = service.addAgent(tenantId, userId, dto);
        assertEquals("Claude Opus 4 via Anthropic", result.getDescription());
    }

    @Test
    void should_autoGenerateLocalDescription_when_selfHosted() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(2L);
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Local Agent")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Local Agent")
                .agentType("GENERAL")
                .provider("ollama")
                .model("deepseek-coder-v2")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        UserAgentConfig result = service.addAgent(tenantId, userId, dto);
        assertEquals("DeepSeek Coder v2 (local)", result.getDescription());
    }

    @Test
    void should_throwException_when_maxAgentsReached() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(8L);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Too Many")
                .agentType("GENERAL")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addAgent(tenantId, userId, dto));
        assertTrue(ex.getMessage().contains("Maximum number of agents"));
    }

    @Test
    void should_throwException_when_duplicateAgentName() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(2L);
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Existing")).thenReturn(true);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Existing")
                .agentType("GENERAL")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addAgent(tenantId, userId, dto));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void should_acceptCustomAgentType_when_addingAgent() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(2L);
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Custom Agent")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Custom Agent")
                .agentType("CUSTOM_TYPE")
                .enabled(true)
                .build();

        UserAgentConfig result = service.addAgent(tenantId, userId, dto);
        assertEquals("CUSTOM_TYPE", result.getAgentType());
    }

    // ============================================================
    // updateAgent
    // ============================================================

    @Test
    void should_updateAgent_when_validRequest() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Old Name", "GENERAL", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "New Name")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("New Name")
                .agentType("GENERAL")
                .displayOrder(3)
                .provider("ollama")
                .model("qwen2.5-coder")
                .hostingType("SELF_HOSTED")
                .baseUrl("http://localhost:11434")
                .description("Qwen 2.5 Coder (local)")
                .enabled(true)
                .build();

        UserAgentConfig result = service.updateAgent(tenantId, userId, agentId, dto);

        assertEquals("New Name", result.getAgentName());
        assertEquals("GENERAL", result.getAgentType());
        assertEquals("ollama", result.getProvider());
        assertEquals("qwen2.5-coder", result.getModel());
        assertEquals("SELF_HOSTED", result.getHostingType());
        assertEquals("http://localhost:11434", result.getBaseUrl());
        assertEquals("Qwen 2.5 Coder (local)", result.getDescription());
    }

    @Test
    void should_updateHostingTypeAndDescription_when_changingProvider() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Agent", "GENERAL", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);
        existing.setProvider("openai");
        existing.setModel("gpt-4o");
        existing.setHostingType("PLATFORM");
        existing.setDescription("GPT-4o via OpenAI");

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        // Switch to custom endpoint with no explicit description
        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Agent")
                .agentType("GENERAL")
                .displayOrder(0)
                .provider("custom-provider")
                .model("my-fine-tuned-model")
                .hostingType("CUSTOM")
                .baseUrl("https://my-api.example.com/v1")
                .apiKeyRef("encrypted:ref:12345")
                .enabled(true)
                .build();

        UserAgentConfig result = service.updateAgent(tenantId, userId, agentId, dto);

        assertEquals("CUSTOM", result.getHostingType());
        assertEquals("https://my-api.example.com/v1", result.getBaseUrl());
        assertEquals("encrypted:ref:12345", result.getApiKeyRef());
        assertEquals("my-fine-tuned-model via Custom endpoint", result.getDescription());
    }

    @Test
    void should_throwNotFound_when_updatingNonexistentAgent() {
        UUID agentId = UUID.randomUUID();
        when(repository.findById(agentId)).thenReturn(Optional.empty());

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("X")
                .agentType("GENERAL")
                .enabled(true)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateAgent(tenantId, userId, agentId, dto));
    }

    @Test
    void should_throwNotFound_when_updatingAgentBelongingToAnotherUser() {
        UUID agentId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Agent", "GENERAL", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(otherUserId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Agent")
                .agentType("GENERAL")
                .enabled(true)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateAgent(tenantId, userId, agentId, dto));
    }

    @Test
    void should_allowSameNameUpdate_when_nameUnchanged() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Same Name", "GENERAL", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Same Name")
                .agentType("GENERAL")
                .displayOrder(0)
                .enabled(true)
                .build();

        UserAgentConfig result = service.updateAgent(tenantId, userId, agentId, dto);

        assertEquals("Same Name", result.getAgentName());
        assertEquals("GENERAL", result.getAgentType());
        // Should not check name uniqueness since name didn't change
        verify(repository, never()).existsByTenantIdAndUserIdAndAgentName(any(), any(), any());
    }

    @Test
    void should_throwException_when_renamingToDuplicateName() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Old Name", "GENERAL", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Taken Name")).thenReturn(true);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Taken Name")
                .agentType("GENERAL")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateAgent(tenantId, userId, agentId, dto));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // ============================================================
    // removeAgent
    // ============================================================

    @Test
    void should_removeAgent_when_moreThanOne() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig agent = buildAgent("To Remove", "GENERAL", 0);
        agent.setId(agentId);
        agent.setTenantId(tenantId);
        agent.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(3L);

        service.removeAgent(tenantId, userId, agentId);

        verify(repository).delete(agent);
    }

    @Test
    void should_throwException_when_removingLastAgent() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig agent = buildAgent("Last Agent", "GENERAL", 0);
        agent.setId(agentId);
        agent.setTenantId(tenantId);
        agent.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.removeAgent(tenantId, userId, agentId));
        assertTrue(ex.getMessage().contains("at least 1 agent"));
    }

    @Test
    void should_throwNotFound_when_removingNonexistentAgent() {
        UUID agentId = UUID.randomUUID();
        when(repository.findById(agentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeAgent(tenantId, userId, agentId));
    }

    @Test
    void should_throwNotFound_when_removingAgentBelongingToAnotherUser() {
        UUID agentId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserAgentConfig agent = buildAgent("Other's Agent", "GENERAL", 0);
        agent.setId(agentId);
        agent.setTenantId(tenantId);
        agent.setUserId(otherUserId);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeAgent(tenantId, userId, agentId));
    }

    // ============================================================
    // resetToDefaults
    // ============================================================

    @Test
    void should_resetToDefaults_when_called() {
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<UserAgentConfig> result = service.resetToDefaults(tenantId, userId);

        verify(repository).deleteByTenantIdAndUserId(tenantId, userId);
        assertEquals(8, result.size());
        assertEquals("Sol", result.get(0).getAgentName());
        assertEquals("github-copilot", result.get(0).getProvider());
        assertEquals("claude-sonnet-4", result.get(0).getModel());
        assertEquals("Claude Sonnet 4 via GitHub Copilot", result.get(0).getDescription());
    }

    // ============================================================
    // getMaxAgentsPerUser
    // ============================================================

    @Test
    void should_returnConfiguredMaxAgents() {
        assertEquals(8, service.getMaxAgentsPerUser());
    }

    @Test
    void should_returnCustomMaxAgents_when_configured() {
        UserAgentConfigService customService = new UserAgentConfigService(repository, 16);
        assertEquals(16, customService.getMaxAgentsPerUser());
    }

    // ============================================================
    // generateDescription
    // ============================================================

    @Test
    void should_generatePlatformDescription_when_providerAndModelSet() {
        assertEquals("Claude Opus 4 via Anthropic",
                UserAgentConfigService.generateDescription("anthropic", "claude-opus-4", "PLATFORM"));
    }

    @Test
    void should_generateLocalDescription_when_selfHosted() {
        assertEquals("Llama 3.3 (local)",
                UserAgentConfigService.generateDescription("ollama", "llama3.3", "SELF_HOSTED"));
    }

    @Test
    void should_generateCustomDescription_when_customHosting() {
        assertEquals("GPT-4o via Custom endpoint",
                UserAgentConfigService.generateDescription("custom-provider", "gpt-4o", "CUSTOM"));
    }

    @Test
    void should_returnProviderOnly_when_modelIsNull() {
        assertEquals("anthropic",
                UserAgentConfigService.generateDescription("anthropic", null, "PLATFORM"));
    }

    @Test
    void should_returnUnconfigured_when_noProviderOrModel() {
        assertEquals("Unconfigured",
                UserAgentConfigService.generateDescription(null, null, null));
    }

    @Test
    void should_returnModelOnly_when_noProviderSet() {
        assertEquals("GPT-4o",
                UserAgentConfigService.generateDescription(null, "gpt-4o", "PLATFORM"));
    }

    @Test
    void should_generateGoogleDescription_when_gemma4Model() {
        assertEquals("Gemma 4 via Google",
                UserAgentConfigService.generateDescription("google", "gemma-4", "PLATFORM"));
    }

    @Test
    void should_generateLocalDescription_when_gemma4OnOllama() {
        assertEquals("Gemma 4 (local)",
                UserAgentConfigService.generateDescription("ollama", "gemma4", "SELF_HOSTED"));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private UserAgentConfig buildAgent(String name, String type, int order) {
        return UserAgentConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .agentName(name)
                .agentType(type)
                .displayOrder(order)
                .enabled(true)
                .build();
    }
}
