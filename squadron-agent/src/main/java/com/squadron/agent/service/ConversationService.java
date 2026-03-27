package com.squadron.agent.service;

import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.repository.ConversationMessageRepository;
import com.squadron.agent.repository.ConversationRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Creates a new conversation for the given task and agent type.
     */
    public Conversation startConversation(UUID tenantId, UUID taskId, UUID userId, String agentType) {
        log.info("Starting new {} conversation for task {} by user {}", agentType, taskId, userId);

        Conversation conversation = Conversation.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType(agentType)
                .status("ACTIVE")
                .totalTokens(0L)
                .build();

        return conversationRepository.save(conversation);
    }

    /**
     * Adds a message to an existing conversation.
     */
    public ConversationMessage addMessage(UUID conversationId, String role, String content, Integer tokenCount) {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .build();

        ConversationMessage saved = messageRepository.save(message);

        // Update total token count on conversation
        if (tokenCount != null && tokenCount > 0) {
            conversationRepository.findById(conversationId).ifPresent(conv -> {
                conv.setTotalTokens(conv.getTotalTokens() + tokenCount);
                conversationRepository.save(conv);
            });
        }

        return saved;
    }

    /**
     * Returns the conversation entity by ID.
     */
    @Transactional(readOnly = true)
    public Conversation getConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }

    /**
     * Returns all messages for a conversation, ordered by creation time.
     */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getConversationMessages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * Returns all conversations for a given task.
     */
    @Transactional(readOnly = true)
    public List<Conversation> getConversationsByTask(UUID taskId) {
        return conversationRepository.findByTaskId(taskId);
    }

    /**
     * Closes a conversation by setting its status to COMPLETED.
     */
    public Conversation closeConversation(UUID id) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));

        conversation.setStatus("COMPLETED");
        log.info("Closed conversation {} (total tokens: {})", id, conversation.getTotalTokens());

        return conversationRepository.save(conversation);
    }
}
