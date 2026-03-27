package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAgentListenerTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private ReviewAgentService reviewAgentService;

    @Mock
    private Message message;

    @Captor
    private ArgumentCaptor<MessageHandler> handlerCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ReviewAgentListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReviewAgentListener(natsConnection, objectMapper, reviewAgentService);
    }

    @Test
    void should_subscribeOnPostConstruct() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        listener.subscribe();

        verify(natsConnection).createDispatcher(any(MessageHandler.class));
        verify(dispatcher).subscribe(ReviewAgentListener.STATE_CHANGED_SUBJECT);
    }

    @Test
    void should_unsubscribeOnPreDestroy() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        listener.subscribe();
        listener.unsubscribe();

        verify(natsConnection).closeDispatcher(dispatcher);
    }

    @Test
    void should_triggerReviewAgent_when_taskTransitionsToReview() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("PROPOSE_CODE");
        event.setToState("REVIEW");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(reviewAgentService).executeReview(eventCaptor.capture());

        TaskStateChangedEvent captured = eventCaptor.getValue();
        assertEquals(taskId, captured.getTaskId());
        assertEquals(tenantId, captured.getTenantId());
        assertEquals(triggeredBy, captured.getTriggeredBy());
        assertEquals("REVIEW", captured.getToState());
    }

    @Test
    void should_ignoreEvent_when_taskTransitionsToOtherState() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("CREATED");
        event.setToState("PLANNING");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(reviewAgentService);
    }

    @Test
    void should_handleInvalidJson_gracefully() {
        when(message.getData()).thenReturn("not valid json!!!".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(reviewAgentService);
    }

    @Test
    void should_handleNullData_gracefully() {
        when(message.getData()).thenReturn(null);

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(reviewAgentService);
    }

    @Test
    void should_handleEmptyData_gracefully() {
        when(message.getData()).thenReturn(new byte[0]);

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(reviewAgentService);
    }

    @Test
    void should_handleExceptionFromReviewAgentService() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("PROPOSE_CODE");
        event.setToState("REVIEW");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        doThrow(new RuntimeException("Service exploded"))
                .when(reviewAgentService).executeReview(any(TaskStateChangedEvent.class));

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verify(reviewAgentService).executeReview(any(TaskStateChangedEvent.class));
    }

    @Test
    void should_notUnsubscribe_when_dispatcherIsNull() {
        // No subscribe() call, so dispatcher is null
        assertDoesNotThrow(() -> listener.unsubscribe());
        verify(natsConnection, never()).closeDispatcher(any());
    }

    @Test
    void should_ignoreProposeCodeState() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("PROPOSE_CODE");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(reviewAgentService);
    }
}
