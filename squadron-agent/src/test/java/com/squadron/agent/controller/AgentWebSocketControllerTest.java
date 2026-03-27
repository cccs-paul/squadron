package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.service.ConversationService;
import com.squadron.agent.service.SquadronConfigService;
import com.squadron.agent.service.SystemPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWebSocketControllerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private SquadronConfigService configService;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AgentProvider agentProvider;

    private AgentWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentWebSocketController(conversationService, configService,
                providerRegistry, promptBuilder, messagingTemplate);
    }

    @Test
    void should_handleChat_newConversation() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Implement this")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
                .agentType("CODING")
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

        when(conversationService.startConversation(any(), eq(taskId), any(), eq("CODING")))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Done"));
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        controller.handleChat(request);

        // Wait for async subscription to complete
        Thread.sleep(500);

        String destination = "/topic/chat/" + conversationId;
        // Verify chunk and done messages were sent
        verify(messagingTemplate, atLeast(2)).convertAndSend(eq(destination), any(StreamChunk.class));
        verify(conversationService).startConversation(any(), eq(taskId), any(), eq("CODING"));
        verify(conversationService).addMessage(eq(conversationId), eq("USER"), eq("Implement this"), any());
    }

    @Test
    void should_handleChat_existingConversation() throws InterruptedException {
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
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
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
        when(configService.resolveAgentConfig(any(), any(), any(), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Continued"));
        when(conversationService.addMessage(any(UUID.class), anyString(), anyString(), any()))
                .thenReturn(assistantMessage);

        controller.handleChat(request);

        // Wait for async subscription to complete
        Thread.sleep(500);

        // Should use getConversation, not startConversation
        verify(conversationService).getConversation(conversationId);
        verify(conversationService, never()).startConversation(any(), any(), any(), any());

        String destination = "/topic/chat/" + conversationId;
        verify(messagingTemplate, atLeast(2)).convertAndSend(eq(destination), any(StreamChunk.class));
    }

    @Test
    void should_handleChat_providerError() throws InterruptedException {
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        when(conversationService.startConversation(any(), eq(taskId), any(), eq("CODING")))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(any(), any(), any(), eq("CODING")))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("System prompt");
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(List.of());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.error(new RuntimeException("Provider failure")));

        controller.handleChat(request);

        // Wait for async subscription to complete
        Thread.sleep(500);

        String destination = "/topic/chat/" + conversationId;
        // Should send error chunk
        ArgumentCaptor<StreamChunk> captor = ArgumentCaptor.forClass(StreamChunk.class);
        verify(messagingTemplate, timeout(1000)).convertAndSend(eq(destination), captor.capture());

        StreamChunk errorChunk = captor.getValue();
        assertEquals("error", errorChunk.getType());
        assertTrue(errorChunk.getContent().contains("Provider failure"));
    }

    @Test
    void should_handleChat_exceptionDuringSetup() {
        UUID taskId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Hello")
                .build();

        when(conversationService.startConversation(any(), eq(taskId), any(), eq("CODING")))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Should not throw exception - it's caught internally
        assertDoesNotThrow(() -> controller.handleChat(request));

        // Should not send any messages since setup failed
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(StreamChunk.class));
    }
}
