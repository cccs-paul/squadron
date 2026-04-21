package com.squadron.agent.ephemeral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeConfigGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_generateOllamaConfig_withDefaultBaseUrl() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "ollama", "gemma3:4b", null, null, "SELF_HOSTED", null, "secret");

        JsonNode root = mapper.readTree(json);
        assertEquals("ollama/gemma3:4b", root.get("model").asText());

        JsonNode provider = root.get("provider").get("ollama");
        assertNotNull(provider);
        assertEquals("@ai-sdk/openai-compatible", provider.get("npm").asText());
        assertEquals("Ollama", provider.get("name").asText());
        assertEquals("http://ollama:11434/v1", provider.get("options").get("baseURL").asText());
        assertEquals("gemma3:4b", provider.get("models").get("gemma3:4b").get("name").asText());
    }

    @Test
    void should_generateOllamaConfig_withCustomBaseUrl() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "ollama", "llama3", "http://my-ollama:11434", null, "SELF_HOSTED", null, "pw");

        JsonNode root = mapper.readTree(json);
        JsonNode options = root.get("provider").get("ollama").get("options");
        assertEquals("http://my-ollama:11434/v1", options.get("baseURL").asText());
    }

    @Test
    void should_notAppendV1_whenBaseUrlAlreadyHasIt() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "ollama", "llama3", "http://my-ollama:11434/v1", null, "SELF_HOSTED", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("http://my-ollama:11434/v1",
                root.get("provider").get("ollama").get("options").get("baseURL").asText());
    }

    @Test
    void should_generateOpenAIConfig_withApiKey() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "openai", "gpt-4o", null, "sk-test-key", "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("openai/gpt-4o", root.get("model").asText());

        JsonNode provider = root.get("provider").get("openai");
        assertEquals("OpenAI", provider.get("name").asText());
        assertEquals("sk-test-key", provider.get("options").get("apiKey").asText());
        assertFalse(provider.has("npm"));
    }

    @Test
    void should_generateOpenAIConfig_withCustomBaseUrl() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "openai", "gpt-4o", "https://my-proxy.com/v1", "key", "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        JsonNode options = root.get("provider").get("openai").get("options");
        assertEquals("key", options.get("apiKey").asText());
        assertEquals("https://my-proxy.com/v1", options.get("baseURL").asText());
    }

    @Test
    void should_generateAnthropicConfig() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "anthropic", "claude-sonnet-4", null, "sk-ant-key", "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("anthropic/claude-sonnet-4", root.get("model").asText());

        JsonNode provider = root.get("provider").get("anthropic");
        assertEquals("Anthropic", provider.get("name").asText());
        assertEquals("sk-ant-key", provider.get("options").get("apiKey").asText());
    }

    @Test
    void should_generateGitHubCopilotConfig() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "github-copilot", "claude-sonnet-4", null, null, "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("github-copilot/claude-sonnet-4", root.get("model").asText());

        JsonNode provider = root.get("provider").get("github-copilot");
        assertEquals("GitHub Copilot", provider.get("name").asText());
    }

    @Test
    void should_generateCustomProviderConfig() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "my-custom", "my-model", "https://custom.ai/v1", "custom-key",
                "CUSTOM", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("my-custom/my-model", root.get("model").asText());

        JsonNode provider = root.get("provider").get("my-custom");
        assertEquals("@ai-sdk/openai-compatible", provider.get("npm").asText());
        assertEquals("my-custom", provider.get("name").asText());
        assertEquals("https://custom.ai/v1", provider.get("options").get("baseURL").asText());
        assertEquals("custom-key", provider.get("options").get("apiKey").asText());
        assertEquals("my-model", provider.get("models").get("my-model").get("name").asText());
    }

    @Test
    void should_includeServerConfig() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "openai", "gpt-4o", null, "key", "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals(4096, root.get("server").get("port").asInt());
        assertEquals("0.0.0.0", root.get("server").get("hostname").asText());
    }

    @Test
    void should_includePermissionAndDisabledFeatures() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "openai", "gpt-4o", null, "key", "CLOUD", null, "pw");

        JsonNode root = mapper.readTree(json);
        assertEquals("grant", root.get("permission").get("*").asText());
        assertFalse(root.get("snapshot").asBoolean());
        assertFalse(root.get("autoupdate").asBoolean());
        assertEquals("disabled", root.get("share").asText());
    }

    @Test
    void should_mapProviderIds_correctly() {
        assertEquals("ollama", OpenCodeConfigGenerator.mapProviderId("ollama"));
        assertEquals("openai", OpenCodeConfigGenerator.mapProviderId("openai"));
        assertEquals("anthropic", OpenCodeConfigGenerator.mapProviderId("anthropic"));
        assertEquals("github-copilot", OpenCodeConfigGenerator.mapProviderId("github-copilot"));
        assertEquals("github-copilot", OpenCodeConfigGenerator.mapProviderId("github_copilot"));
        assertEquals("github-copilot", OpenCodeConfigGenerator.mapProviderId("copilot"));
        assertEquals("azure-openai", OpenCodeConfigGenerator.mapProviderId("azure"));
        assertEquals("azure-openai", OpenCodeConfigGenerator.mapProviderId("azure-openai"));
        assertEquals("google-vertex-ai", OpenCodeConfigGenerator.mapProviderId("google"));
        assertEquals("google-vertex-ai", OpenCodeConfigGenerator.mapProviderId("vertex"));
        assertEquals("deepseek", OpenCodeConfigGenerator.mapProviderId("deepseek"));
        assertEquals("groq", OpenCodeConfigGenerator.mapProviderId("groq"));
        assertEquals("openrouter", OpenCodeConfigGenerator.mapProviderId("openrouter"));
        assertEquals("openai", OpenCodeConfigGenerator.mapProviderId(null));
    }

    @Test
    void should_mapProviderIds_caseInsensitively() {
        assertEquals("ollama", OpenCodeConfigGenerator.mapProviderId("OLLAMA"));
        assertEquals("openai", OpenCodeConfigGenerator.mapProviderId("OpenAI"));
        assertEquals("anthropic", OpenCodeConfigGenerator.mapProviderId("Anthropic"));
    }

    @Test
    void should_passUnknownProviders_asLowercase() {
        assertEquals("mistral", OpenCodeConfigGenerator.mapProviderId("mistral"));
        assertEquals("my-provider", OpenCodeConfigGenerator.mapProviderId("MY-PROVIDER"));
    }

    @Test
    void should_escapeSpecialCharactersInJson() throws Exception {
        String json = OpenCodeConfigGenerator.generate(
                "openai", "gpt-4o", null, "key-with\"quotes", "CLOUD", null, "pw");

        // Should be valid JSON
        JsonNode root = mapper.readTree(json);
        assertNotNull(root);
    }

    @Test
    void should_generateValidJson_forAllProviderTypes() throws Exception {
        String[] providers = {"ollama", "openai", "anthropic", "github-copilot", "custom-provider"};
        for (String provider : providers) {
            String json = OpenCodeConfigGenerator.generate(
                    provider, "test-model", "http://base", "key", "CLOUD", null, "pw");
            JsonNode root = mapper.readTree(json);
            assertNotNull(root.get("model"), "Missing model for provider: " + provider);
            assertNotNull(root.get("provider"), "Missing provider for: " + provider);
        }
    }
}
