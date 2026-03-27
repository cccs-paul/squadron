package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider implementation for OpenAI-compatible endpoints.
 * Works with OpenAI, Azure OpenAI, Claude via GitHub Models, and any
 * other endpoint that implements the OpenAI chat completions API.
 */
@Component
public class OpenAiCompatibleProvider implements AgentProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final ChatClient.Builder chatClientBuilder;

    public OpenAiCompatibleProvider(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending chat request via openai-compatible provider");

        ChatClient chatClient = chatClientBuilder.build();

        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = new Prompt(messages);

        String response = chatClient.prompt(prompt)
                .call()
                .content();

        log.debug("Received response from openai-compatible provider ({} chars)", 
                response != null ? response.length() : 0);

        return response;
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
        log.debug("Sending streaming chat request via openai-compatible provider");

        ChatClient chatClient = chatClientBuilder.build();

        List<Message> messages = buildMessages(systemPrompt, history, userMessage);
        Prompt prompt = new Prompt(messages);

        return chatClient.prompt(prompt)
                .stream()
                .content();
    }

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
