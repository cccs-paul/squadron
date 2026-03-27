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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private SquadronConfigService configService;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private AgentProvider agentProvider;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(conversationService, configService, providerRegistry,
                promptBuilder, natsEventPublisher);
    }

    @Test
    void should_createNewConversation_when_noConversationId() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Implement the feature")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Here is the implementation")
                .tokenCount(6)
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(eq(tenantId), isNull(), eq(userId), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Here is the implementation");
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
        assertEquals(conversationId, response.getConversationId());
        assertEquals(messageId, response.getMessageId());
        assertEquals("ASSISTANT", response.getRole());
        assertEquals("Here is the implementation", response.getContent());
        assertEquals("ACTIVE", response.getStatus());
        verify(conversationService).startConversation(tenantId, taskId, userId, "CODING");
        verify(conversationService).addMessage(eq(conversationId), eq("USER"), eq("Implement the feature"), isNull());
    }

    @Test
    void should_continueConversation_when_conversationIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .conversationId(conversationId)
                .taskId(taskId)
                .agentType("CODING")
                .message("Continue from before")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(100L)
                .provider("openai-compatible")
                .model("gpt-4")
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Continuing implementation")
                .tokenCount(5)
                .build();

        when(conversationService.getConversation(conversationId)).thenReturn(conversation);
        when(configService.resolveAgentConfig(eq(tenantId), isNull(), eq(userId), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Continuing implementation");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
        assertEquals(conversationId, response.getConversationId());
        // Should NOT call startConversation since conversationId was provided
        verify(conversationService).getConversation(conversationId);
    }

    @Test
    void should_handleProviderError_when_aiCallFails() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage errorMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("I encountered an error processing your request. Please try again.")
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenThrow(new RuntimeException("API call failed"));
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(errorMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
        assertTrue(response.getContent().contains("error"));
    }

    @Test
    void should_useSystemPromptOverride_when_configProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Response")
                .build();

        AgentConfigDto configWithOverride = AgentConfigDto.builder()
                .provider("openai-compatible")
                .systemPromptOverride("Custom system prompt")
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(configWithOverride);
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(eq("Custom system prompt"), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Response");
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
        verify(agentProvider).chat(eq("Custom system prompt"), anyList(), anyString(), any(AgentConfigDto.class));
    }

    @Test
    void should_usePlanningPrompt_when_agentTypeIsPlanning() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("PLANNING")
                .message("Analyze this")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("PLANNING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Plan")
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "PLANNING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("Planning system prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(eq("Planning system prompt"), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Plan");
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
        verify(promptBuilder).buildPlanningPrompt(anyString(), anyString());
    }

    @Test
    void should_useDefaultPrompt_when_unknownAgentType() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("UNKNOWN")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("UNKNOWN")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Response")
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "UNKNOWN"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Response");
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
    }

    @Test
    void should_handleNullConfigResolution_when_noConfigFound() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Response")
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(null);
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("Default prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider(null)).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Response");
        when(agentProvider.getProviderName()).thenReturn("openai-compatible");
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        ChatResponse response = agentService.chat(request, tenantId, userId);

        assertNotNull(response);
    }

    @Test
    void should_chatStream_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Implement the feature")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Hello world")
                .tokenCount(2)
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(eq(tenantId), isNull(), eq(userId), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Hello", " world"));
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        Flux<ServerSentEvent<StreamChunk>> result = agentService.chatStream(request, tenantId, userId);

        StepVerifier.create(result)
                .assertNext(sse -> {
                    StreamChunk chunk = sse.data();
                    assertNotNull(chunk);
                    assertEquals(conversationId, chunk.getConversationId());
                    assertEquals("Hello", chunk.getContent());
                    assertEquals("chunk", chunk.getType());
                })
                .assertNext(sse -> {
                    StreamChunk chunk = sse.data();
                    assertNotNull(chunk);
                    assertEquals(conversationId, chunk.getConversationId());
                    assertEquals(" world", chunk.getContent());
                    assertEquals("chunk", chunk.getType());
                })
                .assertNext(sse -> {
                    StreamChunk chunk = sse.data();
                    assertNotNull(chunk);
                    assertEquals(conversationId, chunk.getConversationId());
                    assertEquals("done", chunk.getType());
                    assertEquals(messageId, chunk.getMessageId());
                    assertNotNull(chunk.getTokenCount());
                })
                .verifyComplete();

        verify(conversationService).startConversation(tenantId, taskId, userId, "CODING");
        verify(conversationService).addMessage(eq(conversationId), eq("USER"), eq("Implement the feature"), isNull());
    }

    @Test
    void should_chatStream_handleProviderError() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.error(new RuntimeException("API call failed")));

        Flux<ServerSentEvent<StreamChunk>> result = agentService.chatStream(request, tenantId, userId);

        StepVerifier.create(result)
                .assertNext(sse -> {
                    StreamChunk chunk = sse.data();
                    assertNotNull(chunk);
                    assertEquals(conversationId, chunk.getConversationId());
                    assertEquals("error", chunk.getType());
                    assertTrue(chunk.getContent().contains("API call failed"));
                })
                .verifyComplete();
    }

    @Test
    void should_chatStream_startNewConversation() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("PLANNING")
                .message("Plan this")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("PLANNING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Done")
                .tokenCount(1)
                .build();

        when(conversationService.startConversation(tenantId, taskId, userId, "PLANNING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), any()))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("Planning prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Done"));
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        Flux<ServerSentEvent<StreamChunk>> result = agentService.chatStream(request, tenantId, userId);

        StepVerifier.create(result)
                .assertNext(sse -> assertEquals("chunk", sse.data().getType()))
                .assertNext(sse -> assertEquals("done", sse.data().getType()))
                .verifyComplete();

        // Verify new conversation was started (not getConversation)
        verify(conversationService).startConversation(tenantId, taskId, userId, "PLANNING");
        verify(conversationService, never()).getConversation(any());
    }

    @Test
    void should_chatStream_continueExistingConversation() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .conversationId(conversationId)
                .taskId(taskId)
                .agentType("CODING")
                .message("Continue")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(100L)
                .provider("openai-compatible")
                .model("gpt-4")
                .build();

        ConversationMessage assistantMessage = ConversationMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Continued")
                .tokenCount(2)
                .build();

        when(conversationService.getConversation(conversationId)).thenReturn(conversation);
        when(configService.resolveAgentConfig(eq(tenantId), isNull(), eq(userId), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Continued"));
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        Flux<ServerSentEvent<StreamChunk>> result = agentService.chatStream(request, tenantId, userId);

        StepVerifier.create(result)
                .assertNext(sse -> assertEquals("chunk", sse.data().getType()))
                .assertNext(sse -> assertEquals("done", sse.data().getType()))
                .verifyComplete();

        // Should NOT call startConversation since conversationId was provided
        verify(conversationService).getConversation(conversationId);
        verify(conversationService, never()).startConversation(any(), any(), any(), any());
    }
}
