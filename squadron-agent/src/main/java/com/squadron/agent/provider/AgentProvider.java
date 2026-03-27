package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Abstraction for AI model providers. Each implementation wraps a specific
 * LLM vendor or OpenAI-compatible endpoint.
 */
public interface AgentProvider {

    /**
     * Returns the unique name for this provider (e.g. "openai-compatible").
     */
    String getProviderName();

    /**
     * Sends a synchronous chat request and returns the assistant response content.
     */
    String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config);

    /**
     * Sends a streaming chat request and returns a Flux of content chunks.
     */
    Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config);
}
