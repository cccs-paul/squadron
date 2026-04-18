package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodingAgentServiceTest {

    @Mock
    private PlanService planService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private SquadronConfigService configService;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolExecutionEngine toolExecutionEngine;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Mock
    private AgentProvider agentProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CodingAgentService codingAgentService;

    private UUID taskId;
    private UUID tenantId;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        codingAgentService = new CodingAgentService(
                planService, conversationService, configService, providerRegistry,
                promptBuilder, toolRegistry, toolExecutionEngine, natsEventPublisher,
                objectMapper, workspaceLifecycleService);

        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------------------
    // executeCodeGeneration tests
    // ---------------------------------------------------------------------------

    @Test
    void should_executeCodeGeneration_successfully() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .planContent("1. Create file\n2. Write tests").status("APPROVED").version(1).build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("All changes implemented. [DONE] Created file and wrote tests.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        codingAgentService.executeCodeGeneration(event);

        verify(planService).getLatestPlan(taskId);
        verify(conversationService).startConversation(tenantId, taskId, userId, "CODING");
        verify(agentProvider).chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.coding.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));
    }

    @Test
    void should_skipIfPlanNotApproved() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .planContent("Draft plan").status("DRAFT").version(1).build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        codingAgentService.executeCodeGeneration(event);

        verify(planService).getLatestPlan(taskId);
        verifyNoInteractions(conversationService);
        verifyNoInteractions(providerRegistry);
        verifyNoInteractions(natsEventPublisher);
    }

    @Test
    void should_handlePlanNotFound() {
        TaskStateChangedEvent event = createProposeCodeEvent();
        when(planService.getLatestPlan(taskId)).thenThrow(new RuntimeException("Plan not found"));
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> codingAgentService.executeCodeGeneration(event));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.coding.failed", subjectCaptor.getAllValues().get(0));
    }

    @Test
    void should_publishFailureEvent_onError() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .planContent("Plan content").status("APPROVED").version(1).build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenThrow(new RuntimeException("DB connection failed"));
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        codingAgentService.executeCodeGeneration(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());
        assertEquals("squadron.agent.coding.failed", subjectCaptor.getAllValues().get(0));
        assertFalse(eventCaptor.getAllValues().get(0).isSuccess());
        assertEquals("CODING", eventCaptor.getAllValues().get(0).getAgentType());
    }

    @Test
    void should_publishCompletionEvent_onSuccess() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .planContent("Plan").status("APPROVED").version(1).build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);
        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING")).thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("[DONE] Implementation complete.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        codingAgentService.executeCodeGeneration(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());
        assertEquals("squadron.agent.coding.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));

        AgentCompletedEvent completedEvent = eventCaptor.getAllValues().get(0);
        assertTrue(completedEvent.isSuccess());
        assertEquals("CODING", completedEvent.getAgentType());
        assertEquals(tenantId, completedEvent.getTenantId());
        assertEquals(taskId, completedEvent.getTaskId());
        assertEquals(conversationId, completedEvent.getConversationId());
        assertEquals("squadron-agent", completedEvent.getSource());
    }

    @Test
    void should_useDefaultConfig_when_configIsNull() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .planContent("Plan").status("APPROVED").version(1).build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);
        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING")).thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING")).thenReturn(null);
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any())).thenReturn("[DONE] Done.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> codingAgentService.executeCodeGeneration(event));
        verify(providerRegistry).getProvider("openai-compatible");
    }

    // ---------------------------------------------------------------------------
    // buildCodingPromptWithTools tests
    // ---------------------------------------------------------------------------

    @Test
    void should_buildCodingPromptWithTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder().name("file_read").description("Reads a file from the workspace")
                        .parameters(List.of(ToolParameter.builder().name("path").type("string")
                                .description("The file path to read").required(true).build())).build(),
                ToolDefinition.builder().name("shell_exec").description("Executes a shell command")
                        .parameters(List.of(
                                ToolParameter.builder().name("command").type("string")
                                        .description("The command to execute").required(true).build(),
                                ToolParameter.builder().name("workdir").type("string")
                                        .description("Working directory").required(false).build()
                        )).build()
        );

        String prompt = codingAgentService.buildCodingPromptWithTools(
                "1. Create Main.java\n2. Write tests", tools);

        assertTrue(prompt.contains("file_read"));
        assertTrue(prompt.contains("Reads a file from the workspace"));
        assertTrue(prompt.contains("`path` (string) **required**"));
        assertTrue(prompt.contains("shell_exec"));
        assertTrue(prompt.contains("`workdir` (string) optional"));
        assertTrue(prompt.contains("1. Create Main.java"));
        assertTrue(prompt.contains("[DONE]"));
        assertTrue(prompt.contains("tool_call"));
    }

    @Test
    void should_buildCodingPromptWithTools_emptyTools() {
        String prompt = codingAgentService.buildCodingPromptWithTools(
                "Simple plan", Collections.emptyList());
        assertTrue(prompt.contains("Simple plan"));
        assertTrue(prompt.contains("Available Tools"));
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private TaskStateChangedEvent createProposeCodeEvent() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(userId);
        event.setFromState("PLANNING");
        event.setToState("PROPOSE_CODE");
        return event;
    }

    private Conversation createConversation() {
        return Conversation.builder()
                .id(conversationId).tenantId(tenantId).taskId(taskId).userId(userId)
                .agentType("CODING").status("ACTIVE").totalTokens(0L).build();
    }
}
