package com.squadron.agent.service;

import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.repository.ConversationMessageRepository;
import com.squadron.agent.repository.ConversationRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository messageRepository;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, messageRepository);
    }

    @Test
    void should_startConversation_when_validParameters() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation saved = Conversation.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

        Conversation result = conversationService.startConversation(tenantId, taskId, userId, "CODING");

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
        assertEquals(userId, result.getUserId());
        assertEquals("CODING", result.getAgentType());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0L, result.getTotalTokens());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void should_addMessage_when_validParameters() {
        UUID conversationId = UUID.randomUUID();

        ConversationMessage saved = ConversationMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role("USER")
                .content("Hello")
                .tokenCount(10)
                .build();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .totalTokens(100L)
                .build();

        when(messageRepository.save(any(ConversationMessage.class))).thenReturn(saved);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        ConversationMessage result = conversationService.addMessage(conversationId, "USER", "Hello", 10);

        assertNotNull(result);
        assertEquals("USER", result.getRole());
        assertEquals("Hello", result.getContent());
        assertEquals(10, result.getTokenCount());
        verify(messageRepository).save(any(ConversationMessage.class));
        verify(conversationRepository).findById(conversationId);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void should_addMessage_when_tokenCountIsNull() {
        UUID conversationId = UUID.randomUUID();

        ConversationMessage saved = ConversationMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role("USER")
                .content("Hello")
                .build();

        when(messageRepository.save(any(ConversationMessage.class))).thenReturn(saved);

        ConversationMessage result = conversationService.addMessage(conversationId, "USER", "Hello", null);

        assertNotNull(result);
        verify(messageRepository).save(any(ConversationMessage.class));
        // Should not try to update total tokens when count is null
        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void should_addMessage_when_tokenCountIsZero() {
        UUID conversationId = UUID.randomUUID();

        ConversationMessage saved = ConversationMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role("ASSISTANT")
                .content("Response")
                .tokenCount(0)
                .build();

        when(messageRepository.save(any(ConversationMessage.class))).thenReturn(saved);

        ConversationMessage result = conversationService.addMessage(conversationId, "ASSISTANT", "Response", 0);

        assertNotNull(result);
        // Should not update tokens when count is 0
        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void should_getConversation_when_exists() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .agentType("PLANNING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        Conversation result = conversationService.getConversation(conversationId);

        assertNotNull(result);
        assertEquals(conversationId, result.getId());
    }

    @Test
    void should_throwNotFound_when_conversationDoesNotExist() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> conversationService.getConversation(conversationId));
    }

    @Test
    void should_getConversationMessages_when_called() {
        UUID conversationId = UUID.randomUUID();
        ConversationMessage m1 = ConversationMessage.builder().id(UUID.randomUUID())
                .conversationId(conversationId).role("USER").content("msg1").build();
        ConversationMessage m2 = ConversationMessage.builder().id(UUID.randomUUID())
                .conversationId(conversationId).role("ASSISTANT").content("msg2").build();

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(m1, m2));

        List<ConversationMessage> result = conversationService.getConversationMessages(conversationId);

        assertEquals(2, result.size());
        assertEquals("USER", result.get(0).getRole());
        assertEquals("ASSISTANT", result.get(1).getRole());
    }

    @Test
    void should_getConversationsByTask_when_called() {
        UUID taskId = UUID.randomUUID();
        Conversation c1 = Conversation.builder().id(UUID.randomUUID())
                .taskId(taskId).agentType("PLANNING").status("ACTIVE").totalTokens(0L).build();
        Conversation c2 = Conversation.builder().id(UUID.randomUUID())
                .taskId(taskId).agentType("CODING").status("COMPLETED").totalTokens(0L).build();

        when(conversationRepository.findByTaskId(taskId)).thenReturn(List.of(c1, c2));

        List<Conversation> result = conversationService.getConversationsByTask(taskId);

        assertEquals(2, result.size());
    }

    @Test
    void should_closeConversation_when_exists() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(UUID.randomUUID())
                .status("ACTIVE")
                .totalTokens(500L)
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        Conversation result = conversationService.closeConversation(conversationId);

        assertEquals("COMPLETED", result.getStatus());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void should_throwNotFound_when_closingNonexistentConversation() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> conversationService.closeConversation(conversationId));
    }

    @Test
    void should_returnEmptyList_when_noMessagesForConversation() {
        UUID conversationId = UUID.randomUUID();
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of());

        List<ConversationMessage> result = conversationService.getConversationMessages(conversationId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_returnEmptyList_when_noConversationsForTask() {
        UUID taskId = UUID.randomUUID();
        when(conversationRepository.findByTaskId(taskId)).thenReturn(List.of());

        List<Conversation> result = conversationService.getConversationsByTask(taskId);

        assertTrue(result.isEmpty());
    }
}
