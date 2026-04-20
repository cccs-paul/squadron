package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.client.ResilientOrchestratorClient;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.TicketlessTaskService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TicketlessTaskCreatedEvent;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketlessTaskListenerTest {

    @Mock
    private JetStreamSubscriber jetStreamSubscriber;

    @Mock
    private AgentService agentService;

    @Mock
    private UserAgentConfigRepository agentConfigRepository;

    @Mock
    private ResilientOrchestratorClient orchestratorClient;

    private TicketlessTaskService ticketlessTaskService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private TicketlessTaskListener listener;

    @BeforeEach
    void setUp() {
        ticketlessTaskService = new TicketlessTaskService(agentService, agentConfigRepository, orchestratorClient);
        objectMapper.findAndRegisterModules();
        listener = new TicketlessTaskListener(jetStreamSubscriber, objectMapper, ticketlessTaskService);
    }

    @Test
    void should_subscribe_onInit() {
        listener.subscribe();

        verify(jetStreamSubscriber).subscribe(
                eq(TicketlessTaskListener.SUBJECT),
                eq(TicketlessTaskListener.DURABLE_NAME),
                eq(TicketlessTaskListener.QUEUE_GROUP),
                any());
    }

    @Test
    void should_dispatchToService_when_validMessage() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID agentConfigId = UUID.randomUUID();

        TicketlessTaskCreatedEvent event = new TicketlessTaskCreatedEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setPrompt("Build a login page");
        event.setBranchName("feature/login");
        event.setCreateBranch(true);
        event.setAgentMode("BUILD");
        event.setAgentConfigId(agentConfigId);

        String json = objectMapper.writeValueAsString(event);
        Message message = NatsMessage.builder()
                .subject(TicketlessTaskListener.SUBJECT)
                .data(json.getBytes(StandardCharsets.UTF_8))
                .build();

        listener.handleMessage(message);

        // Verify the service attempted to resolve agent config (proves execute() was called with the event)
        verify(agentConfigRepository).findByIdAndTenantId(eq(agentConfigId), eq(tenantId));
    }

    @Test
    void should_ignoreNullData() {
        Message message = NatsMessage.builder()
                .subject(TicketlessTaskListener.SUBJECT)
                .build();

        listener.handleMessage(message);

        // Service should not have been invoked
        verify(agentConfigRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void should_ignoreEmptyData() {
        Message message = NatsMessage.builder()
                .subject(TicketlessTaskListener.SUBJECT)
                .data(new byte[0])
                .build();

        listener.handleMessage(message);

        verify(agentConfigRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void should_handleInvalidJson_gracefully() {
        Message message = NatsMessage.builder()
                .subject(TicketlessTaskListener.SUBJECT)
                .data("not json".getBytes(StandardCharsets.UTF_8))
                .build();

        // Should not throw
        listener.handleMessage(message);

        verify(agentConfigRepository, never()).findByIdAndTenantId(any(), any());
    }
}
