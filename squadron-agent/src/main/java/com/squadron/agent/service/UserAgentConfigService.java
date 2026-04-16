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
 * defaulting to 8 agents. Agents are general-purpose and can be
 * configured with different AI providers/models for different tasks.
 */
@Service
@Transactional
public class UserAgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserAgentConfigService.class);

    /** Default agent names seeded for new users. Each entry: {name, type, provider, model, description}. */
    private static final List<String[]> DEFAULT_AGENTS = List.of(
            new String[]{"Sol", "GENERAL", "github-copilot", "claude-sonnet-4", "Claude Sonnet 4 via GitHub Copilot"},
            new String[]{"Titan", "GENERAL", "github-copilot", "gpt-4o", "GPT-4o via GitHub Copilot"},
            new String[]{"Vega", "GENERAL", "anthropic", "claude-sonnet-4", "Claude Sonnet 4 via Anthropic"},
            new String[]{"Comet", "GENERAL", "openai", "o3", "o3 via OpenAI"},
            new String[]{"Pulsar", "GENERAL", "github-copilot", "o4-mini", "o4-mini via GitHub Copilot"},
            new String[]{"Quasar", "GENERAL", "google", "gemini-2.5-pro", "Gemini 2.5 Pro via Google"},
            new String[]{"Nova", "GENERAL", "anthropic", "claude-opus-4", "Claude Opus 4 via Anthropic"},
            new String[]{"Nebula", "GENERAL", "ollama", "llama3.3", "Llama 3.3 (local)"}
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
                .hostingType(dto.getHostingType() != null ? dto.getHostingType() : "PLATFORM")
                .baseUrl(dto.getBaseUrl())
                .apiKeyRef(dto.getApiKeyRef())
                .description(dto.getDescription() != null
                        ? dto.getDescription()
                        : generateDescription(dto.getProvider(), dto.getModel(), dto.getHostingType()))
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
        agent.setHostingType(dto.getHostingType() != null ? dto.getHostingType() : "PLATFORM");
        agent.setBaseUrl(dto.getBaseUrl());
        agent.setApiKeyRef(dto.getApiKeyRef());
        agent.setDescription(dto.getDescription() != null
                ? dto.getDescription()
                : generateDescription(dto.getProvider(), dto.getModel(), dto.getHostingType()));
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
            String hostingType = "ollama".equals(def[2]) ? "SELF_HOSTED" : "PLATFORM";
            UserAgentConfig agent = UserAgentConfig.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .agentName(def[0])
                    .agentType(def[1])
                    .displayOrder(i)
                    .provider(def[2])
                    .model(def[3])
                    .hostingType(hostingType)
                    .description(def[4])
                    .enabled(true)
                    .build();
            agents.add(agent);
        }
        List<UserAgentConfig> saved = repository.saveAll(agents);
        log.info("Seeded {} default agents for user {} in tenant {}", saved.size(), userId, tenantId);
        return saved;
    }

    /**
     * Generates a human-readable description from provider, model, and hosting type.
     * e.g. "Claude Opus 4 via Anthropic", "DeepSeek Coder v2 (local)", "GPT-4o via Custom endpoint"
     */
    static String generateDescription(String provider, String model, String hostingType) {
        if (model == null || model.isBlank()) {
            return provider != null ? provider : "Unconfigured";
        }
        String displayModel = humanizeModel(model);
        if ("SELF_HOSTED".equals(hostingType)) {
            return displayModel + " (local)";
        }
        if ("CUSTOM".equals(hostingType)) {
            return displayModel + " via Custom endpoint";
        }
        if (provider != null && !provider.isBlank()) {
            return displayModel + " via " + humanizeProvider(provider);
        }
        return displayModel;
    }

    /** Converts a provider slug to a human-readable name. */
    private static String humanizeProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "github-copilot" -> "GitHub Copilot";
            case "anthropic" -> "Anthropic";
            case "openai" -> "OpenAI";
            case "ollama" -> "Ollama";
            case "cohere" -> "Cohere";
            case "google" -> "Google";
            default -> provider;
        };
    }

    /** Converts a model ID to a friendlier display name. */
    private static String humanizeModel(String model) {
        return switch (model.toLowerCase()) {
            case "claude-opus-4" -> "Claude Opus 4";
            case "claude-sonnet-4" -> "Claude Sonnet 4";
            case "claude-haiku-3.5" -> "Claude Haiku 3.5";
            case "gpt-4o" -> "GPT-4o";
            case "o3" -> "o3";
            case "o4-mini" -> "o4-mini";
            case "gemini-2.5-pro" -> "Gemini 2.5 Pro";
            case "gemma-4", "gemma4" -> "Gemma 4";
            case "command-a-03-2025" -> "Command A";
            case "llama3.3" -> "Llama 3.3";
            case "deepseek-coder-v2" -> "DeepSeek Coder v2";
            case "codestral" -> "Codestral";
            case "qwen2.5-coder" -> "Qwen 2.5 Coder";
            default -> model;
        };
    }
}
