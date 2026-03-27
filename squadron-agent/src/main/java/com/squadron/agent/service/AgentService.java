package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentInvokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final NatsEventPublisher natsEventPublisher;

    public AgentService(ConversationService conversationService,
                        SquadronConfigService configService,
                        AgentProviderRegistry providerRegistry,
                        SystemPromptBuilder promptBuilder,
                        NatsEventPublisher natsEventPublisher) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.natsEventPublisher = natsEventPublisher;
    }

    /**
     * Processes a chat request: manages conversation lifecycle, resolves configuration,
     * calls the AI provider, and persists the response.
     */
    public ChatResponse chat(ChatRequest request, UUID tenantId, UUID userId) {
        log.info("Processing chat request for task {} (agent: {}, user: {})",
                request.getTaskId(), request.getAgentType(), userId);

        // 1. Start or continue conversation
        Conversation conversation;
        if (request.getConversationId() == null) {
            conversation = conversationService.startConversation(
                    tenantId, request.getTaskId(), userId, request.getAgentType());

            // Publish agent invoked event
            publishAgentInvokedEvent(tenantId, request.getTaskId(), userId,
                    request.getAgentType(), conversation.getId());
        } else {
            conversation = conversationService.getConversation(request.getConversationId());
        }

        // 2. Resolve configuration hierarchy: user -> team -> tenant
        AgentConfigDto agentConfig = resolveAgentConfig(tenantId, userId, request.getAgentType());

        // 3. Build system prompt based on agent type
        String systemPrompt = buildSystemPrompt(request.getAgentType(), agentConfig);

        // 4. Load conversation history
        List<ChatMessage> history = loadConversationHistory(conversation.getId());

        // 5. Save user message
        conversationService.addMessage(conversation.getId(), "USER", request.getMessage(), null);

        // 6. Call the AI provider
        AgentProvider provider = providerRegistry.getProvider(agentConfig.getProvider());
        String responseContent;
        try {
            responseContent = provider.chat(systemPrompt, history, request.getMessage(), agentConfig);
        } catch (Exception e) {
            log.error("AI provider call failed for conversation {}", conversation.getId(), e);
            responseContent = "I encountered an error processing your request. Please try again.";
        }

        // 7. Save assistant response
        // Estimate token count (~4 chars per token as rough heuristic)
        int estimatedTokens = (responseContent != null ? responseContent.length() / 4 : 0);
        ConversationMessage assistantMessage = conversationService.addMessage(
                conversation.getId(), "ASSISTANT", responseContent, estimatedTokens);

        // 8. Update conversation provider/model info
        if (conversation.getProvider() == null) {
            conversation.setProvider(provider.getProviderName());
            conversation.setModel(agentConfig.getModel());
        }

        log.info("Chat response generated for conversation {} (estimated tokens: {})",
                conversation.getId(), estimatedTokens);

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .messageId(assistantMessage.getId())
                .role("ASSISTANT")
                .content(responseContent)
                .tokenCount(estimatedTokens)
                .status(conversation.getStatus())
                .build();
    }

    /**
     * Streams chat response chunks via SSE. Creates conversation, calls streaming provider,
     * persists complete response on completion.
     */
    public Flux<ServerSentEvent<StreamChunk>> chatStream(ChatRequest request, UUID tenantId, UUID userId) {
        log.info("Processing streaming chat request for task {} (agent: {}, user: {})",
                request.getTaskId(), request.getAgentType(), userId);

        // 1. Start or continue conversation
        Conversation conversation;
        if (request.getConversationId() == null) {
            conversation = conversationService.startConversation(
                    tenantId, request.getTaskId(), userId, request.getAgentType());
            publishAgentInvokedEvent(tenantId, request.getTaskId(), userId,
                    request.getAgentType(), conversation.getId());
        } else {
            conversation = conversationService.getConversation(request.getConversationId());
        }

        // 2. Resolve configuration
        AgentConfigDto agentConfig = resolveAgentConfig(tenantId, userId, request.getAgentType());

        // 3. Build system prompt
        String systemPrompt = buildSystemPrompt(request.getAgentType(), agentConfig);

        // 4. Load history
        List<ChatMessage> history = loadConversationHistory(conversation.getId());

        // 5. Save user message
        conversationService.addMessage(conversation.getId(), "USER", request.getMessage(), null);

        // 6. Stream from provider
        AgentProvider provider = providerRegistry.getProvider(agentConfig.getProvider());
        StringBuilder fullResponse = new StringBuilder();
        final UUID convId = conversation.getId();

        return provider.chatStream(systemPrompt, history, request.getMessage(), agentConfig)
                .map(chunk -> {
                    fullResponse.append(chunk);
                    return ServerSentEvent.<StreamChunk>builder()
                            .data(StreamChunk.builder()
                                    .conversationId(convId)
                                    .content(chunk)
                                    .type("chunk")
                                    .build())
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    // Save complete response when stream finishes
                    String completeResponse = fullResponse.toString();
                    int estimatedTokens = completeResponse.length() / 4;
                    ConversationMessage msg = conversationService.addMessage(
                            convId, "ASSISTANT", completeResponse, estimatedTokens);

                    return Flux.just(ServerSentEvent.<StreamChunk>builder()
                            .data(StreamChunk.builder()
                                    .conversationId(convId)
                                    .messageId(msg.getId())
                                    .type("done")
                                    .tokenCount(estimatedTokens)
                                    .build())
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("Streaming error for conversation {}", convId, e);
                    return Flux.just(ServerSentEvent.<StreamChunk>builder()
                            .data(StreamChunk.builder()
                                    .conversationId(convId)
                                    .content("Error: " + e.getMessage())
                                    .type("error")
                                    .build())
                            .build());
                });
    }

    private AgentConfigDto resolveAgentConfig(UUID tenantId, UUID userId, String agentType) {
        // Resolve with hierarchy: user -> team -> tenant
        // Note: teamId would come from user context in a full implementation
        AgentConfigDto config = configService.resolveAgentConfig(tenantId, null, userId, agentType);
        if (config == null) {
            config = AgentConfigDto.builder().build();
        }
        return config;
    }

    private String buildSystemPrompt(String agentType, AgentConfigDto config) {
        // Use override if provided
        if (config.getSystemPromptOverride() != null && !config.getSystemPromptOverride().isBlank()) {
            return config.getSystemPromptOverride();
        }

        return switch (agentType.toUpperCase()) {
            case "PLANNING" -> promptBuilder.buildPlanningPrompt("Task", "Analyze and plan implementation");
            case "CODING" -> promptBuilder.buildCodingPrompt("Follow the plan", "Task");
            case "REVIEW" -> promptBuilder.buildReviewPrompt("Review the provided changes");
            case "QA" -> promptBuilder.buildQaPrompt("Verify the changes", "Task requirements");
            default -> "You are a helpful AI assistant for the Squadron platform.";
        };
    }

    private List<ChatMessage> loadConversationHistory(UUID conversationId) {
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId);
        return messages.stream()
                .map(msg -> ChatMessage.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    private void publishAgentInvokedEvent(UUID tenantId, UUID taskId, UUID userId,
                                          String agentType, UUID conversationId) {
        try {
            AgentInvokedEvent event = new AgentInvokedEvent();
            event.setTenantId(tenantId);
            event.setTaskId(taskId);
            event.setUserId(userId);
            event.setAgentType(agentType);
            event.setConversationId(conversationId);
            event.setSource("squadron-agent");

            natsEventPublisher.publishAsync("squadron.agent.invoked", event);
        } catch (Exception e) {
            log.warn("Failed to publish agent invoked event", e);
        }
    }
}
