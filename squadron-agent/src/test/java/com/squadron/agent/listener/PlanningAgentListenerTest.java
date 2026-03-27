package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.PlanService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanningAgentListenerTest {

    @Mock
    private JetStreamSubscriber jetStreamSubscriber;

    @Mock
    private AgentService agentService;

    @Mock
    private PlanService planService;

    @Mock
    private Message message;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private PlanningAgentListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlanningAgentListener(jetStreamSubscriber, objectMapper, agentService, planService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_subscribeOnPostConstruct() {
        listener.subscribe();

        verify(jetStreamSubscriber).subscribe(
                eq(PlanningAgentListener.STATE_CHANGED_SUBJECT),
                eq(PlanningAgentListener.DURABLE_NAME),
                eq(PlanningAgentListener.QUEUE_GROUP),
                any(Consumer.class));
    }

    @Test
    void should_triggerPlanningAgent_whenTaskTransitionsToPLANNING() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversationId)
                .content("Plan: Step 1, Step 2")
                .build();
        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy)))
                .thenReturn(response);

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .version(1)
                .build();
        when(planService.createPlan(eq(tenantId), eq(taskId), eq(conversationId), eq("Plan: Step 1, Step 2")))
                .thenReturn(plan);

        listener.handleMessage(message);

        verify(agentService).chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy));
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(agentService, atLeastOnce()).chat(requestCaptor.capture(), eq(tenantId), eq(triggeredBy));
        ChatRequest capturedRequest = requestCaptor.getValue();
        assertEquals(taskId, capturedRequest.getTaskId());
        assertEquals("PLANNING", capturedRequest.getAgentType());
        assertNotNull(capturedRequest.getMessage());
    }

    @Test
    void should_ignoreNonPlanningTransitions() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("REVIEW");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(agentService);
        verifyNoInteractions(planService);
    }

    @Test
    void should_createDraftPlan_afterSuccessfulChat() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversationId)
                .content("Detailed implementation plan")
                .build();
        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy)))
                .thenReturn(response);

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .version(1)
                .build();
        when(planService.createPlan(eq(tenantId), eq(taskId), eq(conversationId), eq("Detailed implementation plan")))
                .thenReturn(plan);

        listener.handleMessage(message);

        verify(planService).createPlan(tenantId, taskId, conversationId, "Detailed implementation plan");
    }

    @Test
    void should_handleChatFailure_gracefully() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy)))
                .thenThrow(new RuntimeException("AI provider error"));

        // Should not throw
        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(planService);
    }

    @Test
    void should_handleInvalidJson_gracefully() {
        when(message.getData()).thenReturn("not valid json!!!".getBytes(StandardCharsets.UTF_8));

        // Should not throw
        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(agentService);
        verifyNoInteractions(planService);
    }

    @Test
    void should_handleNullResponse_gracefully() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy)))
                .thenReturn(null);

        // Should not throw - null response is handled
        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(planService);
    }

    @Test
    void should_handleNullContent_gracefully() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content(null)
                .build();
        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(triggeredBy)))
                .thenReturn(response);

        // Should not throw - null content is handled
        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(planService);
    }
}
