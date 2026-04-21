package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider implementation for locally-hosted open-source models via Ollama.
 *
 * <p>This provider is always registered. If Spring AI auto-configured an
 * {@link OllamaChatModel} bean (via {@code spring.ai.ollama.*} properties),
 * it is used as the default. Otherwise, dynamic {@link OllamaChatModel}
 * instances are created on-the-fly using the {@code baseUrl} from the
 * agent configuration (defaulting to {@code http://localhost:11434}).
 *
 * <p>Dynamic models are cached by base URL to avoid re-creating them
 * on every request.
 */
@Component
public class OllamaProvider implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final String PROVIDER_NAME = "ollama";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Auto-configured OllamaChatModel (may be null if Ollama is not auto-configured). */
    @Nullable
    private final OllamaChatModel autoConfiguredModel;

    /** Cache of dynamically created OllamaChatModel instances, keyed by base URL. */
    private final Map<String, OllamaChatModel> dynamicModels = new ConcurrentHashMap<>();

    public OllamaProvider(@Nullable OllamaChatModel ollamaChatModel) {
        this.autoConfiguredModel = ollamaChatModel;
        if (ollamaChatModel != null) {
            log.info("Ollama provider initialized with auto-configured model");
        } else {
            log.info("Ollama provider initialized (dynamic mode — no auto-configured model)");
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending chat request via Ollama provider (model: {})",
                config != null && config.getModel() != null ? config.getModel() : "default");

        OllamaChatModel model = resolveModel(config);
        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = buildPrompt(messages, config);

        var response = model.call(prompt);
        String content = response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText()
                : "";

        log.debug("Received response from Ollama provider ({} chars)",
                content != null ? content.length() : 0);

        return content;
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending streaming chat request via Ollama provider (model: {})",
                config != null && config.getModel() != null ? config.getModel() : "default");

        OllamaChatModel model = resolveModel(config);
        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = buildPrompt(messages, config);

        return model.stream(prompt)
                .filter(response -> response.getResult() != null && response.getResult().getOutput() != null)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text -> text != null);
    }

    /**
     * Resolves the OllamaChatModel to use: if an auto-configured model exists
     * and no custom base URL is specified, use it. Otherwise, create (or
     * retrieve from cache) a dynamic model for the target base URL.
     */
    private OllamaChatModel resolveModel(AgentConfigDto config) {
        String baseUrl = (config != null && config.getBaseUrl() != null && !config.getBaseUrl().isBlank())
                ? config.getBaseUrl()
                : DEFAULT_BASE_URL;

        // Use auto-configured model if available and using default URL
        if (autoConfiguredModel != null && DEFAULT_BASE_URL.equals(baseUrl)) {
            return autoConfiguredModel;
        }

        // Create or retrieve a dynamic model for the target base URL
        return dynamicModels.computeIfAbsent(baseUrl, url -> {
            log.info("Creating dynamic Ollama model for base URL: {}", url);
            OllamaApi api = OllamaApi.builder()
                    .baseUrl(url)
                    .build();
            return OllamaChatModel.builder()
                    .ollamaApi(api)
                    .build();
        });
    }

    /**
     * Builds a Prompt with optional Ollama-specific options derived from the
     * agent configuration.
     */
    private Prompt buildPrompt(List<Message> messages, AgentConfigDto config) {
        if (config == null) {
            return new Prompt(messages);
        }

        boolean hasOverrides = config.getModel() != null
                || config.getTemperature() != null
                || config.getMaxTokens() != null;

        if (!hasOverrides) {
            return new Prompt(messages);
        }

        OllamaOptions.Builder optionsBuilder = OllamaOptions.builder();

        if (config.getModel() != null && !config.getModel().isBlank()) {
            optionsBuilder.model(config.getModel());
        }
        if (config.getTemperature() != null) {
            optionsBuilder.temperature(config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            optionsBuilder.numPredict(config.getMaxTokens());
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    /**
     * Converts the provider-agnostic ChatMessage history plus system prompt
     * and user message into Spring AI Message objects.
     */
    private List<Message> buildMessages(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        if (history != null) {
            for (ChatMessage msg : history) {
                switch (msg.getRole().toUpperCase()) {
                    case "USER" -> messages.add(new UserMessage(msg.getContent()));
                    case "ASSISTANT" -> messages.add(new AssistantMessage(msg.getContent()));
                    case "SYSTEM" -> messages.add(new SystemMessage(msg.getContent()));
                    default -> messages.add(new UserMessage(msg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(userMessage));

        return messages;
    }
}
