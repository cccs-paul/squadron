package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.common.event.AgentCompletedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanApprovalListenerTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private Message message;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private PlanApprovalListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlanApprovalListener(natsConnection, objectMapper, webClient);
    }

    @Test
    void should_subscribeOnPostConstruct() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        listener.subscribe();

        verify(natsConnection).createDispatcher(any(MessageHandler.class));
        verify(dispatcher).subscribe(PlanApprovalListener.PLAN_APPROVED_SUBJECT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_triggerTransition_whenPlanApproved() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setConversationId(conversationId);
        event.setAgentType("PLANNING");
        event.setSuccess(true);

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        // Mock the WebClient chain
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn((WebClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        Mono<String> mono = Mono.just("OK");
        when(responseSpec.bodyToMono(String.class)).thenReturn(mono);

        listener.handleMessage(message);

        verify(webClient).post();
        verify(requestBodyUriSpec).uri(eq("/api/tasks/{taskId}/transition"), eq(taskId));

        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        Map<String, Object> capturedBody = bodyCaptor.getValue();
        assertEquals("PROPOSE_CODE", capturedBody.get("targetState"));
        assertEquals("Plan approved by user", capturedBody.get("reason"));
    }

    @Test
    void should_ignoreNonPlanningEvents() throws Exception {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setAgentType("CODING");
        event.setSuccess(true);

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(webClient);
    }

    @Test
    void should_ignoreFailedEvents() throws Exception {
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setUserId(UUID.randomUUID());
        event.setAgentType("PLANNING");
        event.setSuccess(false);

        byte[] data = objectMapper.writeValueAsBytes(event);
        when(message.getData()).thenReturn(data);

        listener.handleMessage(message);

        verifyNoInteractions(webClient);
    }

    @Test
    void should_handleInvalidJson_gracefully() {
        when(message.getData()).thenReturn("not valid json!!!".getBytes(StandardCharsets.UTF_8));

        // Should not throw
        assertDoesNotThrow(() -> listener.handleMessage(message));
        verifyNoInteractions(webClient);
    }

    @Test
    void should_unsubscribeOnPreDestroy() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        listener.subscribe();
        listener.unsubscribe();

        verify(natsConnection).closeDispatcher(dispatcher);
    }

    @Test
    void should_handleNullDispatcherOnPreDestroy() {
        // No subscribe called, dispatcher is null
        assertDoesNotThrow(() -> listener.unsubscribe());
        verify(natsConnection, never()).closeDispatcher(any());
    }
}
