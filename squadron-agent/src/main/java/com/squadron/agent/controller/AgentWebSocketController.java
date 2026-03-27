package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import com.squadron.agent.service.ConversationService;
import com.squadron.agent.service.SquadronConfigService;
import com.squadron.agent.service.SystemPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class AgentWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketController.class);

    private final ConversationService conversationService;
    private final SquadronConfigService configService;
    private final AgentProviderRegistry providerRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final SimpMessagingTemplate messagingTemplate;

    public AgentWebSocketController(ConversationService conversationService,
                                     SquadronConfigService configService,
                                     AgentProviderRegistry providerRegistry,
                                     SystemPromptBuilder promptBuilder,
                                     SimpMessagingTemplate messagingTemplate) {
        this.conversationService = conversationService;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles incoming STOMP messages at /app/chat and streams response chunks
     * to /topic/chat/{conversationId}.
     */
    @MessageMapping("/chat")
    public void handleChat(ChatRequest request) {
        UUID tenantId = request.getTaskId(); // In production, extract from STOMP headers / security context
        UUID userId = UUID.randomUUID(); // In production, extract from security context

        try {
            // 1. Start or continue conversation
            Conversation conversation;
            if (request.getConversationId() == null) {
                conversation = conversationService.startConversation(
                        tenantId, request.getTaskId(), userId, request.getAgentType());
            } else {
                conversation = conversationService.getConversation(request.getConversationId());
            }

            UUID convId = conversation.getId();
            String destination = "/topic/chat/" + convId;

            // 2. Resolve config
            AgentConfigDto agentConfig = configService.resolveAgentConfig(tenantId, null, userId, request.getAgentType());
            if (agentConfig == null) {
                agentConfig = AgentConfigDto.builder().build();
            }

            // 3. Build system prompt
            String systemPrompt = buildSystemPrompt(request.getAgentType(), agentConfig);

            // 4. Load history
            List<ChatMessage> history = conversationService.getConversationMessages(convId).stream()
                    .map(msg -> ChatMessage.builder().role(msg.getRole()).content(msg.getContent()).build())
                    .collect(Collectors.toList());

            // 5. Save user message
            conversationService.addMessage(convId, "USER", request.getMessage(), null);

            // 6. Stream from provider
            AgentProvider provider = providerRegistry.getProvider(agentConfig.getProvider());
            StringBuilder fullResponse = new StringBuilder();

            provider.chatStream(systemPrompt, history, request.getMessage(), agentConfig)
                    .subscribe(
                            chunk -> {
                                fullResponse.append(chunk);
                                messagingTemplate.convertAndSend(destination, StreamChunk.builder()
                                        .conversationId(convId)
                                        .content(chunk)
                                        .type("chunk")
                                        .build());
                            },
                            error -> {
                                log.error("WebSocket streaming error for conversation {}", convId, error);
                                messagingTemplate.convertAndSend(destination, StreamChunk.builder()
                                        .conversationId(convId)
                                        .content("Error: " + error.getMessage())
                                        .type("error")
                                        .build());
                            },
                            () -> {
                                String completeResponse = fullResponse.toString();
                                int estimatedTokens = completeResponse.length() / 4;
                                ConversationMessage msg = conversationService.addMessage(
                                        convId, "ASSISTANT", completeResponse, estimatedTokens);
                                messagingTemplate.convertAndSend(destination, StreamChunk.builder()
                                        .conversationId(convId)
                                        .messageId(msg.getId())
                                        .type("done")
                                        .tokenCount(estimatedTokens)
                                        .build());
                            }
                    );
        } catch (Exception e) {
            log.error("Failed to handle WebSocket chat request", e);
        }
    }

    private String buildSystemPrompt(String agentType, AgentConfigDto config) {
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
}
