package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.service.MergeService;
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
class MergeListenerTest {

    @Mock
    private JetStreamSubscriber jetStreamSubscriber;

    @Mock
    private MergeService mergeService;

    @Mock
    private Message message;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MergeListener listener;

    @BeforeEach
    void setUp() {
        listener = new MergeListener(jetStreamSubscriber, objectMapper, mergeService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_subscribeOnPostConstruct() {
        listener.subscribe();

        verify(jetStreamSubscriber).subscribe(
                eq(MergeListener.STATE_CHANGED_SUBJECT),
                eq(MergeListener.DURABLE_NAME),
                eq(MergeListener.QUEUE_GROUP),
                any(Consumer.class));
    }

    @Test
    void should_triggerMergeService_when_taskTransitionsToMerge() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        ArgumentCaptor<TaskStateChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(TaskStateChangedEvent.class);
        verify(mergeService).executeMerge(eventCaptor.capture());

        TaskStateChangedEvent captured = eventCaptor.getValue();
        assertEquals(taskId, captured.getTaskId());
        assertEquals(tenantId, captured.getTenantId());
        assertEquals(triggeredBy, captured.getTriggeredBy());
        assertEquals("MERGE", captured.getToState());
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

        verifyNoInteractions(mergeService);
    }

    @Test
    void should_ignoreEvent_when_taskTransitionsToReview() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("PROPOSE_CODE");
        event.setToState("REVIEW");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(mergeService);
    }

    @Test
    void should_handleInvalidJson_gracefully() {
        when(message.getData()).thenReturn("not valid json!!!".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(mergeService);
    }

    @Test
    void should_handleNullData_gracefully() {
        when(message.getData()).thenReturn(null);

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(mergeService);
    }

    @Test
    void should_handleEmptyData_gracefully() {
        when(message.getData()).thenReturn(new byte[0]);

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(mergeService);
    }

    @Test
    void should_handleExceptionFromMergeService() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(triggeredBy);
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        doThrow(new RuntimeException("Service exploded"))
                .when(mergeService).executeMerge(any(TaskStateChangedEvent.class));

        assertDoesNotThrow(() -> listener.handleMessage(message));
        verify(mergeService).executeMerge(any(TaskStateChangedEvent.class));
    }
}
