package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.CodingAgentService;
import com.squadron.agent.service.MergeService;
import com.squadron.agent.service.PlanService;
import com.squadron.agent.service.QAAgentService;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class TaskStateDispatcherTest {

    @Mock private JetStreamSubscriber jetStreamSubscriber;
    @Mock private AgentService agentService;
    @Mock private PlanService planService;
    @Mock private CodingAgentService codingAgentService;
    @Mock private ReviewAgentService reviewAgentService;
    @Mock private QAAgentService qaAgentService;
    @Mock private MergeService mergeService;
    @Mock private Message message;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private TaskStateDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TaskStateDispatcher(
                jetStreamSubscriber, objectMapper,
                agentService, planService,
                codingAgentService, reviewAgentService,
                qaAgentService, mergeService);
    }

    // --- Subscription ---

    @Test
    @SuppressWarnings("unchecked")
    void should_subscribeOnPostConstruct() {
        dispatcher.subscribe();

        verify(jetStreamSubscriber).subscribe(
                eq(TaskStateDispatcher.STATE_CHANGED_SUBJECT),
                eq(TaskStateDispatcher.DURABLE_NAME),
                eq(TaskStateDispatcher.QUEUE_GROUP),
                any(Consumer.class));
    }

    // --- PLANNING dispatch ---

    @Test
    void should_triggerPlanningAgent_when_taskTransitionsToPlanning() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TaskStateChangedEvent event = createEvent(taskId, tenantId, userId, "PRIORITIZED", "PLANNING");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversationId)
                .content("Implementation plan here...")
                .build();
        when(agentService.chat(any(ChatRequest.class), eq(tenantId), eq(userId)))
                .thenReturn(response);

        TaskPlan plan = TaskPlan.builder().version(1).id(UUID.randomUUID()).build();
        when(planService.createPlan(eq(tenantId), eq(taskId), eq(conversationId), eq("Implementation plan here...")))
                .thenReturn(plan);

        dispatcher.handleMessage(message);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(agentService).chat(requestCaptor.capture(), eq(tenantId), eq(userId));
        assertEquals(taskId, requestCaptor.getValue().getTaskId());
        assertEquals("PLANNING", requestCaptor.getValue().getAgentType());

        verify(planService).createPlan(tenantId, taskId, conversationId, "Implementation plan here...");
    }

    @Test
    void should_handleNullChatResponse_when_planningAgent() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PRIORITIZED", "PLANNING");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        when(agentService.chat(any(), any(), any())).thenReturn(null);

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(planService);
    }

    @Test
    void should_handleNullContent_when_planningAgent() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PRIORITIZED", "PLANNING");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        ChatResponse response = ChatResponse.builder().conversationId(UUID.randomUUID()).content(null).build();
        when(agentService.chat(any(), any(), any())).thenReturn(response);

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(planService);
    }

    @Test
    void should_handlePlanningAgentFailure_gracefully() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PRIORITIZED", "PLANNING");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        when(agentService.chat(any(), any(), any())).thenThrow(new RuntimeException("LLM error"));

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(planService);
    }

    // --- PROPOSE_CODE dispatch ---

    @Test
    void should_triggerCodingAgent_when_taskTransitionsToProposeCode() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskStateChangedEvent event = createEvent(taskId, tenantId, userId, "PLANNING", "PROPOSE_CODE");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> captor = ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(codingAgentService).executeCodeGeneration(captor.capture());
        assertEquals(taskId, captor.getValue().getTaskId());
        assertEquals(tenantId, captor.getValue().getTenantId());
        assertEquals(userId, captor.getValue().getTriggeredBy());
    }

    @Test
    void should_handleCodingAgentFailure_gracefully() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PLANNING", "PROPOSE_CODE");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        doThrow(new RuntimeException("coding error")).when(codingAgentService).executeCodeGeneration(any());

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
    }

    // --- REVIEW dispatch ---

    @Test
    void should_triggerReviewAgent_when_taskTransitionsToReview() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskStateChangedEvent event = createEvent(taskId, tenantId, userId, "PROPOSE_CODE", "REVIEW");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> captor = ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(reviewAgentService).executeReview(captor.capture());
        assertEquals(taskId, captor.getValue().getTaskId());
    }

    @Test
    void should_handleReviewAgentFailure_gracefully() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROPOSE_CODE", "REVIEW");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        doThrow(new RuntimeException("review error")).when(reviewAgentService).executeReview(any());

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
    }

    // --- QA dispatch ---

    @Test
    void should_triggerQAAgent_when_taskTransitionsToQA() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskStateChangedEvent event = createEvent(taskId, tenantId, userId, "REVIEW", "QA");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> captor = ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(qaAgentService).executeQA(captor.capture());
        assertEquals(taskId, captor.getValue().getTaskId());
    }

    @Test
    void should_handleQAAgentFailure_gracefully() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "REVIEW", "QA");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        doThrow(new RuntimeException("qa error")).when(qaAgentService).executeQA(any());

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
    }

    // --- MERGE dispatch ---

    @Test
    void should_triggerMergeService_when_taskTransitionsToMerge() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskStateChangedEvent event = createEvent(taskId, tenantId, userId, "QA", "MERGE");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> captor = ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(mergeService).executeMerge(captor.capture());
        assertEquals(taskId, captor.getValue().getTaskId());
    }

    @Test
    void should_handleMergeFailure_gracefully() throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "QA", "MERGE");
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));
        doThrow(new RuntimeException("merge error")).when(mergeService).executeMerge(any());

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
    }

    // --- Non-agent states ---

    @ParameterizedTest
    @ValueSource(strings = {"BACKLOG", "PRIORITIZED", "DONE"})
    void should_ignoreNonAgentStates(String state) throws Exception {
        TaskStateChangedEvent event = createEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "WHATEVER", state);
        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        verifyNoInteractions(agentService, planService, codingAgentService,
                reviewAgentService, qaAgentService, mergeService);
    }

    @Test
    void should_ignoreNullToState() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setToState(null);

        when(message.getData()).thenReturn(objectMapper.writeValueAsBytes(event));

        dispatcher.handleMessage(message);

        verifyNoInteractions(agentService, planService, codingAgentService,
                reviewAgentService, qaAgentService, mergeService);
    }

    // --- Error handling ---

    @Test
    void should_handleInvalidJson_gracefully() {
        when(message.getData()).thenReturn("not valid json!!!".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(agentService, codingAgentService, reviewAgentService,
                qaAgentService, mergeService);
    }

    @Test
    void should_handleNullData_gracefully() {
        when(message.getData()).thenReturn(null);

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(agentService, codingAgentService, reviewAgentService,
                qaAgentService, mergeService);
    }

    @Test
    void should_handleEmptyData_gracefully() {
        when(message.getData()).thenReturn(new byte[0]);

        assertDoesNotThrow(() -> dispatcher.handleMessage(message));
        verifyNoInteractions(agentService, codingAgentService, reviewAgentService,
                qaAgentService, mergeService);
    }

    // --- Verify all agent states are handled ---

    @Test
    void should_dispatchAllAgentStates() {
        assertEquals(5, TaskStateDispatcher.AGENT_STATES.size());
        assertTrue(TaskStateDispatcher.AGENT_STATES.contains("PLANNING"));
        assertTrue(TaskStateDispatcher.AGENT_STATES.contains("PROPOSE_CODE"));
        assertTrue(TaskStateDispatcher.AGENT_STATES.contains("REVIEW"));
        assertTrue(TaskStateDispatcher.AGENT_STATES.contains("QA"));
        assertTrue(TaskStateDispatcher.AGENT_STATES.contains("MERGE"));
    }

    // --- Helper ---

    private TaskStateChangedEvent createEvent(UUID taskId, UUID tenantId, UUID userId,
                                               String fromState, String toState) {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(userId);
        event.setFromState(fromState);
        event.setToState(toState);
        return event;
    }
}
