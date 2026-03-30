package com.squadron.agent.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to resolve the ambiguity when both OpenAI and Ollama
 * Spring AI starters are on the classpath. Both starters auto-configure
 * their own {@link ChatModel} bean; Spring AI's {@code ChatClientAutoConfiguration}
 * requires a single primary {@link ChatModel} to build the default
 * {@code ChatClient.Builder}.
 *
 * <p>This designates the OpenAI model as the primary so that the default
 * {@code ChatClient.Builder} (used by {@link com.squadron.agent.provider.OpenAiCompatibleProvider})
 * is backed by OpenAI. The Ollama model remains available for injection
 * by type into {@link com.squadron.agent.provider.OllamaProvider}.
 */
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
