package com.squadron.orchestrator.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.InvalidStateTransitionException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.TaskStateHistoryRepository;
import com.squadron.orchestrator.repository.TaskWorkflowRepository;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Mock
    private TaskWorkflowRepository taskWorkflowRepository;

    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private ObjectMapper objectMapper;
    private WorkflowEngine workflowEngine;

    private String defaultTransitionsJson;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        objectMapper = new ObjectMapper();
        workflowEngine = new WorkflowEngine(
                workflowDefinitionRepository,
                taskWorkflowRepository,
                taskStateHistoryRepository,
                natsEventPublisher,
                objectMapper
        );

        List<Map<String, String>> transitions = new ArrayList<>();
        transitions.add(Map.of("from", "BACKLOG", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "PLANNING"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "BACKLOG"));
        transitions.add(Map.of("from", "PLANNING", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "PLANNING", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "REVIEW"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "PLANNING"));
        transitions.add(Map.of("from", "REVIEW", "to", "QA"));
        transitions.add(Map.of("from", "REVIEW", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "QA", "to", "MERGE"));
        transitions.add(Map.of("from", "QA", "to", "REVIEW"));
        transitions.add(Map.of("from", "MERGE", "to", "DONE"));
        transitions.add(Map.of("from", "MERGE", "to", "QA"));

        defaultTransitionsJson = objectMapper.writeValueAsString(transitions);
    }

    private WorkflowDefinition createDefaultDefinition(UUID tenantId) {
        List<String> states = Arrays.stream(TaskState.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        return WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Default")
                .states(toJson(states))
                .transitions(defaultTransitionsJson)
                .hooks("{}")
                .active(true)
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_initializeWorkflow_when_called() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> {
                    TaskWorkflow tw = inv.getArgument(0);
                    tw.setId(UUID.randomUUID());
                    return tw;
                });
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskWorkflow result = workflowEngine.initializeWorkflow(tenantId, taskId, userId);

        assertNotNull(result);
        assertEquals("BACKLOG", result.getCurrentState());
        assertNull(result.getPreviousState());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
        assertEquals(userId, result.getTransitionedBy());
        assertNotNull(result.getTransitionAt());

        verify(taskWorkflowRepository).save(any(TaskWorkflow.class));

        ArgumentCaptor<TaskStateHistory> historyCaptor = ArgumentCaptor.forClass(TaskStateHistory.class);
        verify(taskStateHistoryRepository).save(historyCaptor.capture());
        TaskStateHistory history = historyCaptor.getValue();
        assertNull(history.getFromState());
        assertEquals("BACKLOG", history.getToState());
        assertEquals(userId, history.getTriggeredBy());
        assertEquals("Workflow initialized", history.getReason());
    }

    @Test
    void should_transitionTask_when_validTransition() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(workflowId)
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));
        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskWorkflow result = workflowEngine.transition(taskId, "PRIORITIZED", userId, "Sprint planning");

        assertEquals("PRIORITIZED", result.getCurrentState());
        assertEquals("BACKLOG", result.getPreviousState());
        assertEquals(userId, result.getTransitionedBy());
        assertNotNull(result.getTransitionAt());

        ArgumentCaptor<TaskStateHistory> historyCaptor = ArgumentCaptor.forClass(TaskStateHistory.class);
        verify(taskStateHistoryRepository).save(historyCaptor.capture());
        assertEquals("BACKLOG", historyCaptor.getValue().getFromState());
        assertEquals("PRIORITIZED", historyCaptor.getValue().getToState());
        assertEquals("Sprint planning", historyCaptor.getValue().getReason());
    }

    @Test
    void should_throwInvalidTransition_when_transitionNotAllowed() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        assertThrows(InvalidStateTransitionException.class,
                () -> workflowEngine.transition(taskId, "DONE", userId, "Skip to done"));
    }

    @Test
    void should_throwNotFound_when_transitionWorkflowMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workflowEngine.transition(taskId, "PRIORITIZED", UUID.randomUUID(), "test"));
    }

    @Test
    void should_publishEvent_when_transitionSuccessful() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));
        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        workflowEngine.transition(taskId, "PRIORITIZED", userId, "reason");

        verify(natsEventPublisher).publishAsync(anyString(), any());
    }

    @Test
    void should_notFailTransition_when_eventPublishFails() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));
        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("NATS unavailable")).when(natsEventPublisher)
                .publishAsync(anyString(), any());

        // Should not throw even if event publishing fails
        TaskWorkflow result = workflowEngine.transition(taskId, "PRIORITIZED", userId, "reason");

        assertEquals("PRIORITIZED", result.getCurrentState());
    }

    @Test
    void should_isTransitionAllowed_returnTrue_when_validTransition() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        assertTrue(workflowEngine.isTransitionAllowed(taskId, "PRIORITIZED"));
    }

    @Test
    void should_isTransitionAllowed_returnFalse_when_invalidTransition() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        assertFalse(workflowEngine.isTransitionAllowed(taskId, "DONE"));
    }

    @Test
    void should_throwNotFound_when_isTransitionAllowedWorkflowMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workflowEngine.isTransitionAllowed(taskId, "PRIORITIZED"));
    }

    @Test
    void should_getAvailableTransitions_when_inBacklog() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        List<String> result = workflowEngine.getAvailableTransitions(taskId);

        assertEquals(1, result.size());
        assertTrue(result.contains("PRIORITIZED"));
    }

    @Test
    void should_getAvailableTransitions_when_inPrioritized() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("PRIORITIZED")
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        List<String> result = workflowEngine.getAvailableTransitions(taskId);

        assertEquals(2, result.size());
        assertTrue(result.contains("PLANNING"));
        assertTrue(result.contains("BACKLOG"));
    }

    @Test
    void should_getAvailableTransitions_when_inDone() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("DONE")
                .build();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));

        List<String> result = workflowEngine.getAvailableTransitions(taskId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_throwNotFound_when_getAvailableTransitionsWorkflowMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> workflowEngine.getAvailableTransitions(taskId));
    }

    @Test
    void should_fallbackToSystemDefault_when_tenantDefinitionMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID systemTenantId = new UUID(0L, 0L);

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .build();

        WorkflowDefinition systemDefinition = createDefaultDefinition(systemTenantId);

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(systemTenantId))
                .thenReturn(Optional.of(systemDefinition));

        List<String> result = workflowEngine.getAvailableTransitions(taskId);

        assertEquals(1, result.size());
        assertTrue(result.contains("PRIORITIZED"));
    }

    @Test
    void should_createDefaultDefinition_when_noDefinitionExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID systemTenantId = new UUID(0L, 0L);

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("BACKLOG")
                .build();

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(systemTenantId))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(inv -> {
                    WorkflowDefinition def = inv.getArgument(0);
                    def.setId(UUID.randomUUID());
                    return def;
                });

        List<String> result = workflowEngine.getAvailableTransitions(taskId);

        assertEquals(1, result.size());
        assertTrue(result.contains("PRIORITIZED"));

        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }

    @Test
    void should_transitionThroughFullWorkflow() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        String[] stateSequence = {"PRIORITIZED", "PLANNING", "PROPOSE_CODE", "REVIEW", "QA", "MERGE", "DONE"};
        String currentState = "BACKLOG";

        for (String targetState : stateSequence) {
            TaskWorkflow workflow = TaskWorkflow.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .taskId(taskId)
                    .currentState(currentState)
                    .transitionAt(Instant.now())
                    .transitionedBy(userId)
                    .build();

            when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
            when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                    .thenReturn(Optional.of(definition));
            when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            TaskWorkflow result = workflowEngine.transition(taskId, targetState, userId, "progressing");

            assertEquals(targetState, result.getCurrentState());
            assertEquals(currentState, result.getPreviousState());

            currentState = targetState;
        }
    }

    @Test
    void should_allowBackwardTransitions() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        // REVIEW -> PROPOSE_CODE (backward transition)
        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState("REVIEW")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        when(taskWorkflowRepository.findByTaskIdForUpdate(taskId)).thenReturn(Optional.of(workflow));
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                .thenReturn(Optional.of(definition));
        when(taskWorkflowRepository.save(any(TaskWorkflow.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskWorkflow result = workflowEngine.transition(taskId, "PROPOSE_CODE", userId, "Changes requested");

        assertEquals("PROPOSE_CODE", result.getCurrentState());
        assertEquals("REVIEW", result.getPreviousState());
    }

    @Test
    void should_getAvailableTransitions_fromAllStates() {
        UUID tenantId = UUID.randomUUID();
        WorkflowDefinition definition = createDefaultDefinition(tenantId);

        Map<String, Integer> expectedCounts = Map.of(
                "BACKLOG", 1,
                "PRIORITIZED", 2,
                "PLANNING", 2,
                "PROPOSE_CODE", 2,
                "REVIEW", 2,
                "QA", 2,
                "MERGE", 2,
                "DONE", 0
        );

        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            UUID taskId = UUID.randomUUID();
            TaskWorkflow workflow = TaskWorkflow.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .taskId(taskId)
                    .currentState(entry.getKey())
                    .build();

            when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
            when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId))
                    .thenReturn(Optional.of(definition));

            List<String> transitions = workflowEngine.getAvailableTransitions(taskId);
            assertEquals(entry.getValue(), transitions.size(),
                    "Expected " + entry.getValue() + " transitions from " + entry.getKey()
                            + " but got " + transitions.size());
        }
    }
}
