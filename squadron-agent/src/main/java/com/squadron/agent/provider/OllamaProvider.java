package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider implementation for locally-hosted open-source models via Ollama.
 *
 * <p>Ollama runs models like Qwen2.5-Coder, DeepSeek Coder, CodeLlama, and
 * StarCoder2 on local hardware. This provider uses Spring AI's native Ollama
 * integration ({@code spring-ai-starter-model-ollama}) for direct communication
 * with the Ollama REST API at {@code http://localhost:11434}.
 *
 * <p>Unlike the OpenAI-compatible provider that uses a generic ChatClient,
 * this provider creates a dedicated ChatClient backed by {@link OllamaChatModel},
 * which supports Ollama-specific features like model pulling, keep-alive
 * configuration, and GPU layer control.
 *
 * <p>To use this provider, ensure Ollama is running locally and the desired
 * model is pulled:
 * <pre>
 *   ollama pull qwen2.5-coder:7b
 *   ollama pull deepseek-coder-v2:16b
 *   ollama pull codellama:13b
 * </pre>
 *
 * <p>This provider is auto-registered when the {@link OllamaChatModel} bean is
 * available (i.e., when the Ollama starter is on the classpath and configured).
 *
 * @see AgentProvider
 * @see AgentProviderRegistry
 */
@Component
@ConditionalOnBean(OllamaChatModel.class)
public class OllamaProvider implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final String PROVIDER_NAME = "ollama";

    private final OllamaChatModel ollamaChatModel;

    public OllamaProvider(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
        log.info("Ollama provider initialized — local open-source model support enabled");
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending chat request via Ollama provider (model: {})",
                config != null && config.getModel() != null ? config.getModel() : "default");

        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = buildPrompt(messages, config);

        ChatClient chatClient = ChatClient.builder(ollamaChatModel).build();

        String response = chatClient.prompt(prompt)
                .call()
                .content();

        log.debug("Received response from Ollama provider ({} chars)",
                response != null ? response.length() : 0);

        return response;
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending streaming chat request via Ollama provider (model: {})",
                config != null && config.getModel() != null ? config.getModel() : "default");

        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = buildPrompt(messages, config);

        ChatClient chatClient = ChatClient.builder(ollamaChatModel).build();

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }

    /**
     * Builds a Prompt with optional Ollama-specific options derived from the
     * agent configuration. This allows per-request model, temperature, and
     * max-token overrides without changing the global application config.
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
