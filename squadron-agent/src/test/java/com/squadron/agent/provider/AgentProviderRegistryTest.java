package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentProviderRegistryTest {

    /**
     * Stub implementation for testing the registry.
     */
    private static class TestProvider implements AgentProvider {
        private final String name;

        TestProvider(String name) {
            this.name = name;
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
            return "response from " + name;
        }

        @Override
        public Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
            return Flux.just("streamed from " + name);
        }
    }

    @Test
    void should_registerProviders_when_constructed() {
        AgentProvider p1 = new TestProvider("openai-compatible");
        AgentProvider p2 = new TestProvider("custom-provider");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(p1, p2));

        assertEquals(p1, registry.getProvider("openai-compatible"));
        assertEquals(p2, registry.getProvider("custom-provider"));
    }

    @Test
    void should_returnRequestedProvider_when_providerExists() {
        AgentProvider provider = new TestProvider("openai-compatible");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(provider));

        AgentProvider result = registry.getProvider("openai-compatible");

        assertEquals(provider, result);
        assertEquals("openai-compatible", result.getProviderName());
    }

    @Test
    void should_returnDefaultProvider_when_nameIsNull() {
        AgentProvider defaultProvider = new TestProvider("openai-compatible");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(defaultProvider));

        AgentProvider result = registry.getProvider(null);

        assertEquals(defaultProvider, result);
    }

    @Test
    void should_returnDefaultProvider_when_nameIsBlank() {
        AgentProvider defaultProvider = new TestProvider("openai-compatible");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(defaultProvider));

        AgentProvider result = registry.getProvider("  ");

        assertEquals(defaultProvider, result);
    }

    @Test
    void should_returnDefaultProvider_when_nameNotFound() {
        AgentProvider defaultProvider = new TestProvider("openai-compatible");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(defaultProvider));

        AgentProvider result = registry.getProvider("nonexistent-provider");

        assertEquals(defaultProvider, result);
    }

    @Test
    void should_fallbackToFirstProvider_when_noOpenAiCompatibleRegistered() {
        AgentProvider provider = new TestProvider("custom-only");
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(provider));

        AgentProvider result = registry.getProvider(null);

        assertEquals(provider, result);
    }

    @Test
    void should_throwException_when_noProvidersRegistered() {
        AgentProviderRegistry registry = new AgentProviderRegistry(Collections.emptyList());

        assertThrows(IllegalStateException.class, () -> registry.getProvider(null));
    }

    @Test
    void should_throwException_when_noProvidersAndNameNotFound() {
        AgentProviderRegistry registry = new AgentProviderRegistry(Collections.emptyList());

        assertThrows(IllegalStateException.class, () -> registry.getProvider("nonexistent"));
    }

    @Test
    void should_preferOpenAiCompatible_when_multipleProvidersRegistered() {
        AgentProvider openai = new TestProvider("openai-compatible");
        AgentProvider custom = new TestProvider("custom-provider");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(custom, openai));

        // When requesting default (null), should return "openai-compatible"
        AgentProvider result = registry.getProvider(null);
        assertEquals("openai-compatible", result.getProviderName());
    }

    @Test
    void should_returnOllamaProvider_when_ollamaRequested() {
        AgentProvider openai = new TestProvider("openai-compatible");
        AgentProvider ollama = new TestProvider("ollama");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(openai, ollama));

        AgentProvider result = registry.getProvider("ollama");

        assertEquals("ollama", result.getProviderName());
        assertEquals(ollama, result);
    }

    @Test
    void should_returnDefaultProvider_when_ollamaRequestedButNotRegistered() {
        AgentProvider openai = new TestProvider("openai-compatible");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(openai));

        // Requesting "ollama" but it's not registered → falls back to default
        AgentProvider result = registry.getProvider("ollama");

        assertEquals("openai-compatible", result.getProviderName());
    }

    @Test
    void should_registerAllThreeProviders_when_multipleProvidersAvailable() {
        AgentProvider openai = new TestProvider("openai-compatible");
        AgentProvider ollama = new TestProvider("ollama");
        AgentProvider custom = new TestProvider("custom-provider");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(openai, ollama, custom));

        assertEquals(openai, registry.getProvider("openai-compatible"));
        assertEquals(ollama, registry.getProvider("ollama"));
        assertEquals(custom, registry.getProvider("custom-provider"));
    }

    @Test
    void should_preferOpenAiCompatible_when_ollamaAndOpenAiRegisteredAndDefaultRequested() {
        AgentProvider openai = new TestProvider("openai-compatible");
        AgentProvider ollama = new TestProvider("ollama");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(ollama, openai));

        // Default (null) should still prefer openai-compatible over ollama
        AgentProvider result = registry.getProvider(null);
        assertEquals("openai-compatible", result.getProviderName());
    }

    @Test
    void should_fallbackToOllama_when_onlyOllamaRegistered() {
        AgentProvider ollama = new TestProvider("ollama");

        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(ollama));

        // Default (null) with only ollama registered → falls back to first provider
        AgentProvider result = registry.getProvider(null);
        assertEquals("ollama", result.getProviderName());
    }
}
