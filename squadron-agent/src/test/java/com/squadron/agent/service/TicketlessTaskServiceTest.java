package com.squadron.agent.service;

import com.squadron.agent.client.ResilientOrchestratorClient;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.event.TicketlessTaskCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketlessTaskServiceTest {

    @Mock
    private AgentService agentService;

    @Mock
    private UserAgentConfigRepository agentConfigRepository;

    @Mock
    private ResilientOrchestratorClient orchestratorClient;

    private TicketlessTaskService service;

    @BeforeEach
    void setUp() {
        service = new TicketlessTaskService(agentService, agentConfigRepository, orchestratorClient);
    }

    private TicketlessTaskCreatedEvent createEvent(String mode) {
        TicketlessTaskCreatedEvent event = new TicketlessTaskCreatedEvent();
        event.setTenantId(UUID.randomUUID());
        event.setTaskId(UUID.randomUUID());
        event.setPrompt("Implement the feature");
        event.setBranchName("feature/test");
        event.setCreateBranch(true);
        event.setAgentMode(mode);
        event.setAgentConfigId(UUID.randomUUID());
        return event;
    }

    @Test
    void should_executePlanMode_when_agentConfigExists() {
        TicketlessTaskCreatedEvent event = createEvent("PLAN");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Sol")
                .agentType("PLANNING")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Here is the plan...")
                .build();

        when(agentService.chat(any(ChatRequest.class), eq(event.getTenantId()), eq(config.getUserId())))
                .thenReturn(response);

        service.execute(event);

        // Should update status to PLANNING first, then COMPLETED
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "PLANNING")));
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "COMPLETED")));
    }

    @Test
    void should_executeBuildMode_when_agentConfigExists() {
        TicketlessTaskCreatedEvent event = createEvent("BUILD");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Titan")
                .agentType("CODING")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Code generated successfully")
                .build();

        when(agentService.chat(any(ChatRequest.class), eq(event.getTenantId()), eq(config.getUserId())))
                .thenReturn(response);

        service.execute(event);

        // Should update status to BUILDING first, then COMPLETED
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "BUILDING")));
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "COMPLETED")));
    }

    @Test
    void should_failWhenAgentConfigNotFound() {
        TicketlessTaskCreatedEvent event = createEvent("PLAN");

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.empty());

        service.execute(event);

        // Should update to PLANNING, then FAILED
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "PLANNING")));
        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "FAILED")));
        verify(agentService, never()).chat(any(), any(), any());
    }

    @Test
    void should_failWhenAgentServiceThrowsException() {
        TicketlessTaskCreatedEvent event = createEvent("BUILD");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Titan")
                .agentType("CODING")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));
        when(agentService.chat(any(ChatRequest.class), any(), any()))
                .thenThrow(new RuntimeException("AI provider error"));

        service.execute(event);

        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "FAILED")));
    }

    @Test
    void should_failWhenResponseContentIsNull() {
        TicketlessTaskCreatedEvent event = createEvent("PLAN");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Sol")
                .agentType("PLANNING")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));
        when(agentService.chat(any(ChatRequest.class), any(), any()))
                .thenReturn(ChatResponse.builder().content(null).build());

        service.execute(event);

        verify(orchestratorClient).updateTicketlessStatus(
                eq(event.getTaskId().toString()), eq(Map.of("status", "FAILED")));
    }

    @Test
    void should_includesBranchInfoInPrompt() {
        TicketlessTaskCreatedEvent event = createEvent("BUILD");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Titan")
                .agentType("CODING")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Done")
                .build();
        when(agentService.chat(any(ChatRequest.class), any(), any())).thenReturn(response);

        service.execute(event);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(agentService).chat(captor.capture(), any(), any());
        String message = captor.getValue().getMessage();
        assertTrue(message.contains("feature/test"));
        assertTrue(message.contains("create new branch"));
    }

    @Test
    void should_useAgentTypeFromConfig() {
        TicketlessTaskCreatedEvent event = createEvent("BUILD");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Custom")
                .agentType("REVIEW")
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Done")
                .build();
        when(agentService.chat(any(ChatRequest.class), any(), any())).thenReturn(response);

        service.execute(event);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(agentService).chat(captor.capture(), any(), any());
        assertEquals("REVIEW", captor.getValue().getAgentType());
    }

    @Test
    void should_defaultAgentType_when_configHasNoType() {
        TicketlessTaskCreatedEvent event = createEvent("PLAN");

        UserAgentConfig config = UserAgentConfig.builder()
                .id(event.getAgentConfigId())
                .tenantId(event.getTenantId())
                .userId(UUID.randomUUID())
                .agentName("Custom")
                .agentType(null) // no type
                .build();

        when(agentConfigRepository.findByIdAndTenantId(event.getAgentConfigId(), event.getTenantId()))
                .thenReturn(Optional.of(config));

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .content("Done")
                .build();
        when(agentService.chat(any(ChatRequest.class), any(), any())).thenReturn(response);

        service.execute(event);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(agentService).chat(captor.capture(), any(), any());
        assertEquals("PLANNING", captor.getValue().getAgentType());
    }
}
