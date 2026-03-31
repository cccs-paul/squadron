package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentProgressDto;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.AgentSessionManager;
import com.squadron.agent.service.ConversationService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents/chat")
public class AgentChatController {

    private final AgentService agentService;
    private final ConversationService conversationService;
    private final AgentSessionManager agentSessionManager;

    public AgentChatController(AgentService agentService,
                               ConversationService conversationService,
                               AgentSessionManager agentSessionManager) {
        this.agentService = agentService;
        this.conversationService = conversationService;
        this.agentSessionManager = agentSessionManager;
    }

    /**
     * Sends a chat message to an AI agent. Creates a new conversation if
     * conversationId is not provided in the request.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        ChatResponse response = agentService.chat(request, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Returns a conversation and its messages.
     */
    @GetMapping("/conversation/{conversationId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Conversation>> getConversation(@PathVariable UUID conversationId) {
        Conversation conversation = conversationService.getConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    /**
     * Returns all messages for a conversation, ordered by creation time.
     */
    @GetMapping("/conversation/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<List<ConversationMessage>>> getConversationMessages(
            @PathVariable UUID conversationId) {
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    /**
     * Lists all conversations for a given task.
     */
    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<List<Conversation>>> getConversationsByTask(@PathVariable UUID taskId) {
        List<Conversation> conversations = conversationService.getConversationsByTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    /**
     * Streams chat response chunks via Server-Sent Events.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public Flux<ServerSentEvent<StreamChunk>> chatStream(@Valid @RequestBody ChatRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        return agentService.chatStream(request, tenantId, userId);
    }

    /**
     * Closes an active conversation.
     */
    @PostMapping("/conversation/{conversationId}/close")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Conversation>> closeConversation(@PathVariable UUID conversationId) {
        Conversation conversation = conversationService.closeConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    /**
     * Returns the current progress for an active agent session.
     */
    @GetMapping("/sessions/{conversationId}/progress")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<AgentProgressDto>> getSessionProgress(
            @PathVariable UUID conversationId) {
        AgentProgressDto progress = agentSessionManager.getProgress(conversationId);
        if (progress == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    /**
     * Interrupts an active agent session, cancelling the stream.
     */
    @PostMapping("/sessions/{conversationId}/interrupt")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Boolean>> interruptSession(@PathVariable UUID conversationId) {
        boolean cancelled = agentSessionManager.cancelStream(conversationId);
        return ResponseEntity.ok(ApiResponse.success(cancelled));
    }
}
