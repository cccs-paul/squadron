package com.squadron.agent.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that collects all {@link AgentProvider} beans and provides
 * lookup by provider name.
 */
@Component
public class AgentProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentProviderRegistry.class);

    private final Map<String, AgentProvider> providers;

    public AgentProviderRegistry(List<AgentProvider> providerList) {
        this.providers = new HashMap<>();
        for (AgentProvider provider : providerList) {
            providers.put(provider.getProviderName(), provider);
            log.info("Registered agent provider: {}", provider.getProviderName());
        }
    }

    /**
     * Returns the provider for the given name, or the default provider
     * if the name is null or not found.
     */
    public AgentProvider getProvider(String name) {
        if (name == null || name.isBlank()) {
            return getDefaultProvider();
        }
        AgentProvider provider = providers.get(name);
        if (provider == null) {
            log.warn("Provider '{}' not found, falling back to default", name);
            return getDefaultProvider();
        }
        return provider;
    }

    private AgentProvider getDefaultProvider() {
        AgentProvider defaultProvider = providers.get("openai-compatible");
        if (defaultProvider == null && !providers.isEmpty()) {
            defaultProvider = providers.values().iterator().next();
        }
        if (defaultProvider == null) {
            throw new IllegalStateException("No agent providers registered");
        }
        return defaultProvider;
    }
}
