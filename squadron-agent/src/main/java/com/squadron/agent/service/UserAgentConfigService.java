package com.squadron.agent.service;

import com.squadron.agent.dto.UserAgentConfigDto;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing a user's personal AI agent squadron.
 * Each user gets a configurable set of agents with unique names,
 * defaulting to 8 agents (one per known type + 2 extras).
 */
@Service
@Transactional
public class UserAgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserAgentConfigService.class);

    /** The canonical set of agent types known to Squadron. */
    private static final List<String> KNOWN_AGENT_TYPES = List.of(
            "PLANNING", "CODING", "REVIEW", "QA", "MERGE", "COVERAGE");

    /** Default agent names seeded for new users, one per known type + 2 extras. */
    private static final List<String[]> DEFAULT_AGENTS = List.of(
            new String[]{"Architect", "PLANNING"},
            new String[]{"Maverick", "CODING"},
            new String[]{"Hawkeye", "REVIEW"},
            new String[]{"Gremlin", "QA"},
            new String[]{"Stitch", "MERGE"},
            new String[]{"Radar", "COVERAGE"},
            new String[]{"Phoenix", "CODING"},
            new String[]{"Oracle", "REVIEW"}
    );

    private final UserAgentConfigRepository repository;
    private final int maxAgentsPerUser;

    public UserAgentConfigService(
            UserAgentConfigRepository repository,
            @Value("${squadron.agents.max-per-user:8}") int maxAgentsPerUser) {
        this.repository = repository;
        this.maxAgentsPerUser = maxAgentsPerUser;
    }

    /**
     * Returns the user's agent squadron, seeding defaults if the user has none.
     */
    @Transactional
    public List<UserAgentConfig> getUserSquadron(UUID tenantId, UUID userId) {
        List<UserAgentConfig> agents = repository.findByTenantIdAndUserIdOrderByDisplayOrderAsc(tenantId, userId);
        if (agents.isEmpty()) {
            log.info("No squadron found for user {}; seeding defaults", userId);
            agents = seedDefaults(tenantId, userId);
        }
        return agents;
    }

    /**
     * Adds a new agent to the user's squadron.
     */
    public UserAgentConfig addAgent(UUID tenantId, UUID userId, UserAgentConfigDto dto) {
        long count = repository.countByTenantIdAndUserId(tenantId, userId);
        if (count >= maxAgentsPerUser) {
            throw new IllegalArgumentException(
                    "Maximum number of agents (" + maxAgentsPerUser + ") reached for this user");
        }

        validateAgentType(dto.getAgentType());

        if (repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, dto.getAgentName())) {
            throw new IllegalArgumentException(
                    "Agent name '" + dto.getAgentName() + "' already exists for this user");
        }

        UserAgentConfig agent = UserAgentConfig.builder()
                .tenantId(tenantId)
                .userId(userId)
                .agentName(dto.getAgentName())
                .agentType(dto.getAgentType())
                .displayOrder(dto.getDisplayOrder())
                .provider(dto.getProvider())
                .model(dto.getModel())
                .maxTokens(dto.getMaxTokens())
                .temperature(dto.getTemperature())
                .systemPromptOverride(dto.getSystemPromptOverride())
                .enabled(dto.isEnabled())
                .build();

        UserAgentConfig saved = repository.save(agent);
        log.info("Added agent '{}' ({}) for user {} in tenant {}", saved.getAgentName(),
                saved.getAgentType(), userId, tenantId);
        return saved;
    }

    /**
     * Updates an existing agent configuration.
     */
    public UserAgentConfig updateAgent(UUID tenantId, UUID userId, UUID agentId, UserAgentConfigDto dto) {
        UserAgentConfig agent = repository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAgentConfig", agentId));

        // Verify ownership
        if (!agent.getTenantId().equals(tenantId) || !agent.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("UserAgentConfig", agentId);
        }

        validateAgentType(dto.getAgentType());

        // Check name uniqueness if name changed
        if (!agent.getAgentName().equals(dto.getAgentName())) {
            if (repository.existsByTenantIdAndUserIdAndAgentName(tenantId, userId, dto.getAgentName())) {
                throw new IllegalArgumentException(
                        "Agent name '" + dto.getAgentName() + "' already exists for this user");
            }
        }

        agent.setAgentName(dto.getAgentName());
        agent.setAgentType(dto.getAgentType());
        agent.setDisplayOrder(dto.getDisplayOrder());
        agent.setProvider(dto.getProvider());
        agent.setModel(dto.getModel());
        agent.setMaxTokens(dto.getMaxTokens());
        agent.setTemperature(dto.getTemperature());
        agent.setSystemPromptOverride(dto.getSystemPromptOverride());
        agent.setEnabled(dto.isEnabled());

        UserAgentConfig saved = repository.save(agent);
        log.info("Updated agent '{}' ({}) for user {}", saved.getAgentName(), saved.getId(), userId);
        return saved;
    }

    /**
     * Removes an agent from the user's squadron.
     * Users must keep at least 1 agent.
     */
    public void removeAgent(UUID tenantId, UUID userId, UUID agentId) {
        UserAgentConfig agent = repository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAgentConfig", agentId));

        if (!agent.getTenantId().equals(tenantId) || !agent.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("UserAgentConfig", agentId);
        }

        long count = repository.countByTenantIdAndUserId(tenantId, userId);
        if (count <= 1) {
            throw new IllegalArgumentException("Cannot remove the last agent; at least 1 agent is required");
        }

        repository.delete(agent);
        log.info("Removed agent '{}' ({}) from user {}'s squadron", agent.getAgentName(), agentId, userId);
    }

    /**
     * Resets the user's squadron to default agents, removing all current agents.
     */
    public List<UserAgentConfig> resetToDefaults(UUID tenantId, UUID userId) {
        repository.deleteByTenantIdAndUserId(tenantId, userId);
        log.info("Reset squadron to defaults for user {} in tenant {}", userId, tenantId);
        return seedDefaults(tenantId, userId);
    }

    /**
     * Returns the configured maximum agents per user.
     */
    public int getMaxAgentsPerUser() {
        return maxAgentsPerUser;
    }

    /**
     * Seeds default agents for a new user.
     */
    private List<UserAgentConfig> seedDefaults(UUID tenantId, UUID userId) {
        List<UserAgentConfig> agents = new ArrayList<>();
        int limit = Math.min(DEFAULT_AGENTS.size(), maxAgentsPerUser);
        for (int i = 0; i < limit; i++) {
            String[] def = DEFAULT_AGENTS.get(i);
            UserAgentConfig agent = UserAgentConfig.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .agentName(def[0])
                    .agentType(def[1])
                    .displayOrder(i)
                    .enabled(true)
                    .build();
            agents.add(agent);
        }
        List<UserAgentConfig> saved = repository.saveAll(agents);
        log.info("Seeded {} default agents for user {} in tenant {}", saved.size(), userId, tenantId);
        return saved;
    }

    private void validateAgentType(String agentType) {
        if (!KNOWN_AGENT_TYPES.contains(agentType)) {
            throw new IllegalArgumentException(
                    "Invalid agent type '" + agentType + "'. Must be one of: " + KNOWN_AGENT_TYPES);
        }
    }
}
