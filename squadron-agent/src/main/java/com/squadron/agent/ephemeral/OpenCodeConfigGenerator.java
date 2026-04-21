package com.squadron.agent.ephemeral;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Generates OpenCode JSON configuration files from Squadron agent configuration.
 * Maps Squadron provider/model concepts to OpenCode's config schema.
 */
public class OpenCodeConfigGenerator {

    private OpenCodeConfigGenerator() {}

    /**
     * Generates an OpenCode JSON config string for the given agent parameters.
     *
     * @param provider     the LLM provider name (e.g., "ollama", "openai", "anthropic")
     * @param model        the model name (e.g., "gemma3:4b", "gpt-4o", "claude-sonnet-4")
     * @param baseUrl      the provider base URL (nullable — uses defaults)
     * @param apiKey       the API key (nullable — not needed for local providers)
     * @param hostingType  CLOUD, SELF_HOSTED, or CUSTOM
     * @param systemPrompt custom system instructions (nullable)
     * @param serverPassword password for the OpenCode server HTTP auth
     * @return a JSON string for opencode.json
     */
    public static String generate(String provider, String model, String baseUrl,
                                   String apiKey, String hostingType,
                                   String systemPrompt, String serverPassword) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"$schema\": \"https://opencode.ai/config.json\",\n");

        // Map Squadron provider to OpenCode provider config
        String openCodeProviderId = mapProviderId(provider);
        String openCodeModelId = openCodeProviderId + "/" + model;

        json.append("  \"model\": \"").append(escapeJson(openCodeModelId)).append("\",\n");

        // Provider configuration
        json.append("  \"provider\": {\n");
        json.append("    \"").append(escapeJson(openCodeProviderId)).append("\": {\n");

        switch (openCodeProviderId) {
            case "ollama" -> generateOllamaProvider(json, model, baseUrl);
            case "openai" -> generateCloudProvider(json, "OpenAI", baseUrl, apiKey,
                    "https://api.openai.com/v1");
            case "anthropic" -> generateCloudProvider(json, "Anthropic", baseUrl, apiKey,
                    "https://api.anthropic.com/v1");
            case "github-copilot" -> generateGitHubCopilotProvider(json, apiKey);
            default -> generateCustomProvider(json, provider, model, baseUrl, apiKey);
        }

        json.append("    }\n");
        json.append("  },\n");

        // Server configuration
        json.append("  \"server\": {\n");
        json.append("    \"port\": 4096,\n");
        json.append("    \"hostname\": \"0.0.0.0\"\n");
        json.append("  },\n");

        // Auto-grant all permissions (sandbox is ephemeral and isolated)
        json.append("  \"permission\": {\n");
        json.append("    \"*\": \"grant\"\n");
        json.append("  },\n");

        // Disable features unnecessary in ephemeral containers
        json.append("  \"snapshot\": false,\n");
        json.append("  \"autoupdate\": false,\n");
        json.append("  \"share\": \"disabled\"\n");

        json.append("}\n");
        return json.toString();
    }

    private static void generateOllamaProvider(StringBuilder json, String model, String baseUrl) {
        String ollamaBase = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "http://ollama:11434";
        // OpenCode uses the /v1 suffix for OpenAI-compatible endpoint
        String ollamaV1 = ollamaBase.endsWith("/v1") ? ollamaBase : ollamaBase + "/v1";

        json.append("      \"npm\": \"@ai-sdk/openai-compatible\",\n");
        json.append("      \"name\": \"Ollama\",\n");
        json.append("      \"options\": {\n");
        json.append("        \"baseURL\": \"").append(escapeJson(ollamaV1)).append("\"\n");
        json.append("      },\n");
        json.append("      \"models\": {\n");
        json.append("        \"").append(escapeJson(model)).append("\": {\n");
        json.append("          \"name\": \"").append(escapeJson(model)).append("\"\n");
        json.append("        }\n");
        json.append("      }\n");
    }

    private static void generateCloudProvider(StringBuilder json, String displayName,
                                                String baseUrl, String apiKey,
                                                String defaultBaseUrl) {
        json.append("      \"name\": \"").append(escapeJson(displayName)).append("\",\n");
        json.append("      \"options\": {\n");
        if (apiKey != null && !apiKey.isBlank()) {
            json.append("        \"apiKey\": \"").append(escapeJson(apiKey)).append("\"");
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            if (apiKey != null && !apiKey.isBlank()) json.append(",\n");
            json.append("        \"baseURL\": \"").append(escapeJson(baseUrl)).append("\"");
        }
        json.append("\n      }\n");
    }

    private static void generateGitHubCopilotProvider(StringBuilder json, String apiKey) {
        json.append("      \"name\": \"GitHub Copilot\"\n");
        // GitHub Copilot uses OAuth device flow — API key is the OAuth token
        // OpenCode handles this natively when the provider is "github-copilot"
    }

    private static void generateCustomProvider(StringBuilder json, String provider,
                                                 String model, String baseUrl, String apiKey) {
        json.append("      \"npm\": \"@ai-sdk/openai-compatible\",\n");
        json.append("      \"name\": \"").append(escapeJson(provider)).append("\",\n");
        json.append("      \"options\": {\n");
        if (baseUrl != null && !baseUrl.isBlank()) {
            json.append("        \"baseURL\": \"").append(escapeJson(baseUrl)).append("\"");
            if (apiKey != null && !apiKey.isBlank()) json.append(",\n");
            else json.append("\n");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            json.append("        \"apiKey\": \"").append(escapeJson(apiKey)).append("\"\n");
        }
        json.append("      },\n");
        json.append("      \"models\": {\n");
        json.append("        \"").append(escapeJson(model)).append("\": {\n");
        json.append("          \"name\": \"").append(escapeJson(model)).append("\"\n");
        json.append("        }\n");
        json.append("      }\n");
    }

    /**
     * Maps Squadron provider identifiers to OpenCode provider IDs.
     */
    static String mapProviderId(String squadronProvider) {
        if (squadronProvider == null) return "openai";
        return switch (squadronProvider.toLowerCase()) {
            case "ollama" -> "ollama";
            case "openai" -> "openai";
            case "anthropic" -> "anthropic";
            case "github-copilot", "github_copilot", "copilot" -> "github-copilot";
            case "azure", "azure-openai", "azure_openai" -> "azure-openai";
            case "google", "google-vertex", "vertex" -> "google-vertex-ai";
            case "deepseek" -> "deepseek";
            case "groq" -> "groq";
            case "openrouter" -> "openrouter";
            default -> squadronProvider.toLowerCase();
        };
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
