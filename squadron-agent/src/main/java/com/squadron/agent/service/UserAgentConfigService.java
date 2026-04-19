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
import com.squadron.common.security.TenantScopedLookup;

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
            new String[]{"Sol", "GENERAL", "ollama", "gemma4:e2b", "Gemma 4 E2B (local)"},
            new String[]{"Titan", "GENERAL", "ollama", "qwen2.5-coder:7b", "Qwen 2.5 Coder 7B (local)"}
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
        UserAgentConfig agent = TenantScopedLookup.findByIdScoped(agentId, repository::findById, repository::findByIdAndTenantId, () -> new ResourceNotFoundException("UserAgentConfig", agentId));

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
        UserAgentConfig agent = TenantScopedLookup.findByIdScoped(agentId, repository::findById, repository::findByIdAndTenantId, () -> new ResourceNotFoundException("UserAgentConfig", agentId));

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
        repository.flush();
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
            // Anthropic
            case "claude-opus-4" -> "Claude Opus 4";
            case "claude-opus-4.6" -> "Claude Opus 4.6";
            case "claude-sonnet-4" -> "Claude Sonnet 4";
            case "claude-haiku-3.5" -> "Claude Haiku 3.5";
            // OpenAI
            case "gpt-4.1" -> "GPT-4.1";
            case "gpt-4.1-mini" -> "GPT-4.1 Mini";
            case "gpt-4.1-nano" -> "GPT-4.1 Nano";
            case "gpt-4o" -> "GPT-4o";
            case "gpt-4o-mini" -> "GPT-4o Mini";
            case "o3" -> "o3";
            case "o3-mini" -> "o3 Mini";
            case "o4-mini" -> "o4-mini";
            case "codex-mini" -> "Codex Mini";
            // Google
            case "gemini-2.5-pro" -> "Gemini 2.5 Pro";
            case "gemini-2.5-flash" -> "Gemini 2.5 Flash";
            case "gemini-2.0-flash" -> "Gemini 2.0 Flash";
            case "gemma4:e2b", "gemma4", "gemma-4" -> "Gemma 4 E2B";
            case "gemma4:e4b" -> "Gemma 4 E4B";
            case "gemma4:26b" -> "Gemma 4 26B";
            case "gemma3:4b", "gemma3", "gemma-3" -> "Gemma 3 4B";
            // Mistral
            case "mistral-large-latest" -> "Mistral Large";
            case "mistral-medium-latest" -> "Mistral Medium";
            case "mistral-small-latest" -> "Mistral Small";
            case "codestral-latest", "codestral:latest", "codestral" -> "Codestral";
            case "devstral-small-latest", "devstral:latest", "devstral" -> "Devstral";
            case "mistral:latest", "mistral" -> "Mistral 7B";
            // Cohere
            case "command-a-03-2025" -> "Command A";
            case "command-a-reasoning-08-2025" -> "Command A Reasoning";
            case "command-a-vision-07-2025" -> "Command A Vision";
            case "command-r7b-12-2024" -> "Command R7B";
            case "command-r-plus-08-2024", "command-r-plus" -> "Command R+";
            case "command-r-08-2024", "command-r" -> "Command R";
            // DeepSeek
            case "deepseek-chat" -> "DeepSeek V3";
            case "deepseek-reasoner" -> "DeepSeek R1";
            case "deepseek-coder", "deepseek-coder-v2:16b", "deepseek-coder-v2" -> "DeepSeek Coder";
            case "deepseek-r1:8b" -> "DeepSeek R1 8B";
            case "deepseek-r1:14b" -> "DeepSeek R1 14B";
            // xAI
            case "grok-3" -> "Grok 3";
            case "grok-3-mini" -> "Grok 3 Mini";
            // Meta Llama
            case "llama3.3:latest", "llama3.3:70b", "llama3.3" -> "Llama 3.3 70B";
            case "llama3.1:8b", "llama3.1" -> "Llama 3.1 8B";
            // Qwen
            case "qwen2.5-coder:1.5b" -> "Qwen 2.5 Coder 1.5B";
            case "qwen2.5-coder:7b", "qwen2.5-coder" -> "Qwen 2.5 Coder 7B";
            case "qwen2.5-coder:14b" -> "Qwen 2.5 Coder 14B";
            case "qwen2.5-coder:32b" -> "Qwen 2.5 Coder 32B";
            // Microsoft Phi
            case "phi-4-mini" -> "Phi-4 Mini";
            case "phi-4:latest", "phi-4" -> "Phi-4 14B";
            // StarCoder
            case "starcoder2:7b", "starcoder2" -> "StarCoder2 7B";
            default -> model;
        };
    }
}
