package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.TaskStatsDto;
import com.squadron.orchestrator.dto.TaskWorkflowDto;
import com.squadron.orchestrator.dto.TransitionRequest;
import com.squadron.orchestrator.engine.TaskState;
import com.squadron.orchestrator.engine.WorkflowEngine;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.repository.TaskRepository;
import com.squadron.orchestrator.repository.TaskStateHistoryRepository;
import com.squadron.orchestrator.repository.TaskWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskWorkflowRepository taskWorkflowRepository;

    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Mock
    private WorkflowEngine workflowEngine;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskWorkflowRepository,
                taskStateHistoryRepository, workflowEngine);
    }

    @Test
    void should_createTask_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Fix bug")
                .description("Fix the login bug")
                .priority("HIGH")
                .build();

        Task savedTask = Task.builder()
                .id(taskId)
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Fix bug")
                .description("Fix the login bug")
                .priority("HIGH")
                .build();

        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        Task result = taskService.createTask(request, userId);

        assertNotNull(result);
        assertEquals("Fix bug", result.getTitle());
        assertEquals(taskId, result.getId());
        verify(taskRepository).save(any(Task.class));
        verify(workflowEngine).initializeWorkflow(tenantId, taskId, userId);
    }

    @Test
    void should_getTask_when_exists() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .title("Existing Task")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        Task result = taskService.getTask(taskId);

        assertEquals(taskId, result.getId());
        assertEquals("Existing Task", result.getTitle());
    }

    @Test
    void should_throwNotFound_when_taskMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTask(taskId));
    }

    @Test
    void should_listTasksByProject() {
        UUID projectId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).projectId(projectId).title("T1").build(),
                Task.builder().id(UUID.randomUUID()).projectId(projectId).title("T2").build()
        );

        when(taskRepository.findByProjectId(projectId)).thenReturn(tasks);

        List<Task> result = taskService.listTasksByProject(projectId);

        assertEquals(2, result.size());
    }

    @Test
    void should_listTasksByTeam() {
        UUID teamId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).teamId(teamId).title("T1").build()
        );

        when(taskRepository.findByTeamId(teamId)).thenReturn(tasks);

        List<Task> result = taskService.listTasksByTeam(teamId);

        assertEquals(1, result.size());
    }

    @Test
    void should_listTasksByAssignee() {
        UUID assigneeId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).assigneeId(assigneeId).title("T1").build()
        );

        when(taskRepository.findByAssigneeId(assigneeId)).thenReturn(tasks);

        List<Task> result = taskService.listTasksByAssignee(assigneeId);

        assertEquals(1, result.size());
    }

    @Test
    void should_updateTask_when_allFieldsProvided() {
        UUID taskId = UUID.randomUUID();
        UUID newAssigneeId = UUID.randomUUID();

        Task existing = Task.builder()
                .id(taskId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .title("Old Title")
                .description("Old Desc")
                .priority("LOW")
                .build();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .title("New Title")
                .description("New Desc")
                .assigneeId(newAssigneeId)
                .priority("HIGH")
                .labels("[\"urgent\"]")
                .externalId("EXT-1")
                .externalUrl("https://example.com")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenReturn(existing);

        Task result = taskService.updateTask(taskId, request);

        assertEquals("New Title", existing.getTitle());
        assertEquals("New Desc", existing.getDescription());
        assertEquals(newAssigneeId, existing.getAssigneeId());
        assertEquals("HIGH", existing.getPriority());
        assertEquals("[\"urgent\"]", existing.getLabels());
        assertEquals("EXT-1", existing.getExternalId());
        assertEquals("https://example.com", existing.getExternalUrl());
    }

    @Test
    void should_updateTask_when_onlyTitleProvided() {
        UUID taskId = UUID.randomUUID();

        Task existing = Task.builder()
                .id(taskId)
                .title("Old Title")
                .description("Old Desc")
                .priority("LOW")
                .build();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .title("New Title")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenReturn(existing);

        taskService.updateTask(taskId, request);

        assertEquals("New Title", existing.getTitle());
        assertEquals("Old Desc", existing.getDescription()); // unchanged
        assertEquals("LOW", existing.getPriority()); // unchanged
    }

    @Test
    void should_throwNotFound_when_updatingMissingTask() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        CreateTaskRequest request = CreateTaskRequest.builder().title("T").build();

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.updateTask(taskId, request));
    }

    @Test
    void should_deleteTask_when_exists() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).title("To Delete").build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        taskService.deleteTask(taskId);

        verify(taskRepository).delete(task);
    }

    @Test
    void should_throwNotFound_when_deletingMissingTask() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.deleteTask(taskId));
    }

    @Test
    void should_transitionTask_when_validRequest() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TransitionRequest request = TransitionRequest.builder()
                .taskId(taskId)
                .targetState("PLANNING")
                .reason("Ready for planning")
                .build();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .currentState("PLANNING")
                .previousState("PRIORITIZED")
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        when(workflowEngine.transition(taskId, "PLANNING", userId, "Ready for planning"))
                .thenReturn(workflow);

        TaskWorkflow result = taskService.transitionTask(request, userId);

        assertEquals("PLANNING", result.getCurrentState());
        assertEquals("PRIORITIZED", result.getPreviousState());
        verify(workflowEngine).transition(taskId, "PLANNING", userId, "Ready for planning");
    }

    @Test
    void should_getTaskWorkflow_when_exists() {
        UUID taskId = UUID.randomUUID();
        UUID transitionedBy = UUID.randomUUID();
        Instant now = Instant.now();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .currentState("REVIEW")
                .previousState("PROPOSE_CODE")
                .transitionAt(now)
                .transitionedBy(transitionedBy)
                .metadata("{}")
                .build();

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));

        TaskWorkflowDto result = taskService.getTaskWorkflow(taskId);

        assertEquals(taskId, result.getTaskId());
        assertEquals("REVIEW", result.getCurrentState());
        assertEquals("PROPOSE_CODE", result.getPreviousState());
        assertEquals(now, result.getTransitionAt());
        assertEquals(transitionedBy, result.getTransitionedBy());
        assertEquals("{}", result.getMetadata());
    }

    @Test
    void should_throwNotFound_when_workflowMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTaskWorkflow(taskId));
    }

    @Test
    void should_getTaskHistory_when_workflowExists() {
        UUID taskId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();

        TaskWorkflow workflow = TaskWorkflow.builder()
                .id(workflowId)
                .taskId(taskId)
                .currentState("QA")
                .build();

        TaskStateHistory h1 = TaskStateHistory.builder()
                .id(UUID.randomUUID())
                .taskWorkflowId(workflowId)
                .fromState("REVIEW")
                .toState("QA")
                .triggeredBy(UUID.randomUUID())
                .build();

        TaskStateHistory h2 = TaskStateHistory.builder()
                .id(UUID.randomUUID())
                .taskWorkflowId(workflowId)
                .fromState("PROPOSE_CODE")
                .toState("REVIEW")
                .triggeredBy(UUID.randomUUID())
                .build();

        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.of(workflow));
        when(taskStateHistoryRepository.findByTaskWorkflowIdOrderByCreatedAtDesc(workflowId))
                .thenReturn(List.of(h1, h2));

        List<TaskStateHistory> result = taskService.getTaskHistory(taskId);

        assertEquals(2, result.size());
        assertEquals("QA", result.get(0).getToState());
        assertEquals("REVIEW", result.get(1).getToState());
    }

    @Test
    void should_throwNotFound_when_historyWorkflowMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskWorkflowRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getTaskHistory(taskId));
    }

    @Test
    void should_getAvailableTransitions() {
        UUID taskId = UUID.randomUUID();
        List<String> transitions = List.of("PLANNING", "BACKLOG");

        when(workflowEngine.getAvailableTransitions(taskId)).thenReturn(transitions);

        List<String> result = taskService.getAvailableTransitions(taskId);

        assertEquals(2, result.size());
        assertEquals("PLANNING", result.get(0));
        assertEquals("BACKLOG", result.get(1));
    }

    @Test
    void should_getTasksByState_when_tasksExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();
        UUID taskId3 = UUID.randomUUID();

        List<Task> tasks = List.of(
                Task.builder().id(taskId1).tenantId(tenantId).title("T1").build(),
                Task.builder().id(taskId2).tenantId(tenantId).title("T2").build(),
                Task.builder().id(taskId3).tenantId(tenantId).title("T3").build()
        );

        List<TaskWorkflow> workflows = List.of(
                TaskWorkflow.builder().taskId(taskId1).currentState("PLANNING").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build(),
                TaskWorkflow.builder().taskId(taskId2).currentState("REVIEW").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build()
        );

        when(taskRepository.findByTenantId(tenantId)).thenReturn(tasks);
        when(taskWorkflowRepository.findByTenantId(tenantId)).thenReturn(workflows);

        Map<String, List<Task>> result = taskService.getTasksByState(tenantId);

        // taskId3 has no workflow, so defaults to BACKLOG
        assertEquals(1, result.get("BACKLOG").size());
        assertEquals("T3", result.get("BACKLOG").get(0).getTitle());
        assertEquals(1, result.get("PLANNING").size());
        assertEquals("T1", result.get("PLANNING").get(0).getTitle());
        assertEquals(1, result.get("REVIEW").size());
        assertEquals("T2", result.get("REVIEW").get(0).getTitle());
        // All other states should have empty lists
        assertEquals(0, result.get("DONE").size());
        assertEquals(0, result.get("QA").size());
    }

    @Test
    void should_getTasksByState_when_noTasks() {
        UUID tenantId = UUID.randomUUID();

        when(taskRepository.findByTenantId(tenantId)).thenReturn(Collections.emptyList());
        when(taskWorkflowRepository.findByTenantId(tenantId)).thenReturn(Collections.emptyList());

        Map<String, List<Task>> result = taskService.getTasksByState(tenantId);

        // All states should be present with empty lists
        for (TaskState state : TaskState.values()) {
            assertNotNull(result.get(state.name()));
            assertEquals(0, result.get(state.name()).size());
        }
    }

    @Test
    void should_getTaskStats_when_tasksExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();
        UUID taskId3 = UUID.randomUUID();

        List<Task> tasks = List.of(
                Task.builder().id(taskId1).tenantId(tenantId).title("T1").priority("HIGH").build(),
                Task.builder().id(taskId2).tenantId(tenantId).title("T2").priority("HIGH").build(),
                Task.builder().id(taskId3).tenantId(tenantId).title("T3").priority("LOW").build()
        );

        List<TaskWorkflow> workflows = List.of(
                TaskWorkflow.builder().taskId(taskId1).currentState("PLANNING").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build(),
                TaskWorkflow.builder().taskId(taskId2).currentState("PLANNING").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build(),
                TaskWorkflow.builder().taskId(taskId3).currentState("REVIEW").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build()
        );

        when(taskRepository.findByTenantId(tenantId)).thenReturn(tasks);
        when(taskWorkflowRepository.findByTenantId(tenantId)).thenReturn(workflows);

        TaskStatsDto result = taskService.getTaskStats(tenantId);

        assertEquals(3, result.getTotal());
        assertEquals(2L, result.getByState().get("PLANNING"));
        assertEquals(1L, result.getByState().get("REVIEW"));
        assertEquals(2L, result.getByPriority().get("HIGH"));
        assertEquals(1L, result.getByPriority().get("LOW"));
    }

    @Test
    void should_getTaskStats_when_tasksHaveNoPriority() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();

        List<Task> tasks = List.of(
                Task.builder().id(taskId1).tenantId(tenantId).title("T1").build(),
                Task.builder().id(taskId2).tenantId(tenantId).title("T2").build()
        );

        List<TaskWorkflow> workflows = List.of(
                TaskWorkflow.builder().taskId(taskId1).currentState("BACKLOG").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build(),
                TaskWorkflow.builder().taskId(taskId2).currentState("BACKLOG").tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build()
        );

        when(taskRepository.findByTenantId(tenantId)).thenReturn(tasks);
        when(taskWorkflowRepository.findByTenantId(tenantId)).thenReturn(workflows);

        TaskStatsDto result = taskService.getTaskStats(tenantId);

        assertEquals(2, result.getTotal());
        assertEquals(2L, result.getByState().get("BACKLOG"));
        assertNotNull(result.getByPriority());
        assertEquals(0, result.getByPriority().size());
    }
}
