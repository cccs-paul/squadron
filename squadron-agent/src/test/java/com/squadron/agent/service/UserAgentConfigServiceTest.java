package com.squadron.agent.service;

import com.squadron.agent.dto.UserAgentConfigDto;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                buildAgent("Architect", "PLANNING", 0),
                buildAgent("Maverick", "CODING", 1)
        );
        when(repository.findByTenantIdAndUserIdOrderByDisplayOrderAsc(tenantId, userId))
                .thenReturn(agents);

        List<UserAgentConfig> result = service.getUserSquadron(tenantId, userId);

        assertEquals(2, result.size());
        assertEquals("Architect", result.get(0).getAgentName());
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
        assertEquals("Architect", result.get(0).getAgentName());
        assertEquals("PLANNING", result.get(0).getAgentType());
        assertEquals("Maverick", result.get(1).getAgentName());
        assertEquals("CODING", result.get(1).getAgentType());
        assertEquals("Oracle", result.get(7).getAgentName());
        assertEquals("REVIEW", result.get(7).getAgentType());
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
                .agentType("CODING")
                .displayOrder(5)
                .provider("openai-compatible")
                .model("gpt-4o")
                .maxTokens(4096)
                .temperature(0.3)
                .enabled(true)
                .build();

        UserAgentConfig result = service.addAgent(tenantId, userId, dto);

        assertNotNull(result);
        assertEquals("New Agent", result.getAgentName());
        assertEquals("CODING", result.getAgentType());
        assertEquals("openai-compatible", result.getProvider());
        verify(repository).save(any(UserAgentConfig.class));
    }

    @Test
    void should_throwException_when_maxAgentsReached() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(8L);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Too Many")
                .agentType("CODING")
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
                .agentType("CODING")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addAgent(tenantId, userId, dto));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void should_throwException_when_invalidAgentType() {
        when(repository.countByTenantIdAndUserId(tenantId, userId)).thenReturn(2L);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Bad Type Agent")
                .agentType("INVALID")
                .enabled(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addAgent(tenantId, userId, dto));
        assertTrue(ex.getMessage().contains("Invalid agent type"));
    }

    // ============================================================
    // updateAgent
    // ============================================================

    @Test
    void should_updateAgent_when_validRequest() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Old Name", "CODING", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "New Name")).thenReturn(false);
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("New Name")
                .agentType("REVIEW")
                .displayOrder(3)
                .provider("ollama")
                .model("qwen2.5-coder:7b")
                .enabled(true)
                .build();

        UserAgentConfig result = service.updateAgent(tenantId, userId, agentId, dto);

        assertEquals("New Name", result.getAgentName());
        assertEquals("REVIEW", result.getAgentType());
        assertEquals("ollama", result.getProvider());
    }

    @Test
    void should_throwNotFound_when_updatingNonexistentAgent() {
        UUID agentId = UUID.randomUUID();
        when(repository.findById(agentId)).thenReturn(Optional.empty());

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("X")
                .agentType("CODING")
                .enabled(true)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateAgent(tenantId, userId, agentId, dto));
    }

    @Test
    void should_throwNotFound_when_updatingAgentBelongingToAnotherUser() {
        UUID agentId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Agent", "CODING", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(otherUserId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Agent")
                .agentType("CODING")
                .enabled(true)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateAgent(tenantId, userId, agentId, dto));
    }

    @Test
    void should_allowSameNameUpdate_when_nameUnchanged() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Same Name", "CODING", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Same Name")
                .agentType("REVIEW")
                .displayOrder(0)
                .enabled(true)
                .build();

        UserAgentConfig result = service.updateAgent(tenantId, userId, agentId, dto);

        assertEquals("Same Name", result.getAgentName());
        assertEquals("REVIEW", result.getAgentType());
        // Should not check name uniqueness since name didn't change
        verify(repository, never()).existsByTenantIdAndUserIdAndAgentName(any(), any(), any());
    }

    @Test
    void should_throwException_when_renamingToDuplicateName() {
        UUID agentId = UUID.randomUUID();
        UserAgentConfig existing = buildAgent("Old Name", "CODING", 0);
        existing.setId(agentId);
        existing.setTenantId(tenantId);
        existing.setUserId(userId);

        when(repository.findById(agentId)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, "Taken Name")).thenReturn(true);

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Taken Name")
                .agentType("CODING")
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
        UserAgentConfig agent = buildAgent("To Remove", "CODING", 0);
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
        UserAgentConfig agent = buildAgent("Last Agent", "CODING", 0);
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
        UserAgentConfig agent = buildAgent("Other's Agent", "CODING", 0);
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
        assertEquals("Architect", result.get(0).getAgentName());
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
