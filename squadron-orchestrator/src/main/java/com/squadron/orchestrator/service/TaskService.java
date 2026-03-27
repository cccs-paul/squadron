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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskWorkflowRepository taskWorkflowRepository;
    private final TaskStateHistoryRepository taskStateHistoryRepository;
    private final WorkflowEngine workflowEngine;

    public TaskService(TaskRepository taskRepository,
                       TaskWorkflowRepository taskWorkflowRepository,
                       TaskStateHistoryRepository taskStateHistoryRepository,
                       WorkflowEngine workflowEngine) {
        this.taskRepository = taskRepository;
        this.taskWorkflowRepository = taskWorkflowRepository;
        this.taskStateHistoryRepository = taskStateHistoryRepository;
        this.workflowEngine = workflowEngine;
    }

    public Task createTask(CreateTaskRequest request, UUID userId) {
        log.info("Creating task '{}' for project {}", request.getTitle(), request.getProjectId());

        Task task = Task.builder()
                .tenantId(request.getTenantId())
                .teamId(request.getTeamId())
                .projectId(request.getProjectId())
                .externalId(request.getExternalId())
                .externalUrl(request.getExternalUrl())
                .title(request.getTitle())
                .description(request.getDescription())
                .assigneeId(request.getAssigneeId())
                .priority(request.getPriority())
                .labels(request.getLabels())
                .build();

        task = taskRepository.save(task);

        workflowEngine.initializeWorkflow(task.getTenantId(), task.getId(), userId);

        return task;
    }

    @Transactional(readOnly = true)
    public Task getTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Transactional(readOnly = true)
    public List<Task> listTasksByProject(UUID projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<Task> listTasksByTeam(UUID teamId) {
        return taskRepository.findByTeamId(teamId);
    }

    @Transactional(readOnly = true)
    public List<Task> listTasksByAssignee(UUID assigneeId) {
        return taskRepository.findByAssigneeId(assigneeId);
    }

    public Task updateTask(UUID id, CreateTaskRequest request) {
        Task task = getTask(id);

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getAssigneeId() != null) {
            task.setAssigneeId(request.getAssigneeId());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getLabels() != null) {
            task.setLabels(request.getLabels());
        }
        if (request.getExternalId() != null) {
            task.setExternalId(request.getExternalId());
        }
        if (request.getExternalUrl() != null) {
            task.setExternalUrl(request.getExternalUrl());
        }

        return taskRepository.save(task);
    }

    public void deleteTask(UUID id) {
        Task task = getTask(id);
        taskRepository.delete(task);
        log.info("Deleted task {} ({})", task.getTitle(), id);
    }

    public TaskWorkflow transitionTask(TransitionRequest request, UUID userId) {
        return workflowEngine.transition(
                request.getTaskId(),
                request.getTargetState(),
                userId,
                request.getReason()
        );
    }

    @Transactional(readOnly = true)
    public TaskWorkflowDto getTaskWorkflow(UUID taskId) {
        TaskWorkflow workflow = taskWorkflowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkflow", taskId));

        return TaskWorkflowDto.builder()
                .taskId(workflow.getTaskId())
                .currentState(workflow.getCurrentState())
                .previousState(workflow.getPreviousState())
                .transitionAt(workflow.getTransitionAt())
                .transitionedBy(workflow.getTransitionedBy())
                .metadata(workflow.getMetadata())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TaskStateHistory> getTaskHistory(UUID taskId) {
        TaskWorkflow workflow = taskWorkflowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkflow", taskId));

        return taskStateHistoryRepository.findByTaskWorkflowIdOrderByCreatedAtDesc(workflow.getId());
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableTransitions(UUID taskId) {
        return workflowEngine.getAvailableTransitions(taskId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<Task>> getTasksByState(UUID tenantId) {
        List<Task> tasks = taskRepository.findByTenantId(tenantId);
        List<TaskWorkflow> workflows = taskWorkflowRepository.findByTenantId(tenantId);

        // Build a map of taskId -> currentState
        Map<UUID, String> taskStateMap = workflows.stream()
                .collect(Collectors.toMap(TaskWorkflow::getTaskId, TaskWorkflow::getCurrentState));

        // Group tasks by their workflow state
        Map<String, List<Task>> result = new LinkedHashMap<>();
        for (TaskState state : TaskState.values()) {
            result.put(state.name(), new ArrayList<>());
        }

        for (Task task : tasks) {
            String state = taskStateMap.getOrDefault(task.getId(), TaskState.BACKLOG.name());
            result.computeIfAbsent(state, k -> new ArrayList<>()).add(task);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public TaskStatsDto getTaskStats(UUID tenantId) {
        List<Task> tasks = taskRepository.findByTenantId(tenantId);
        List<TaskWorkflow> workflows = taskWorkflowRepository.findByTenantId(tenantId);

        Map<UUID, String> taskStateMap = workflows.stream()
                .collect(Collectors.toMap(TaskWorkflow::getTaskId, TaskWorkflow::getCurrentState));

        Map<String, Long> byState = tasks.stream()
                .map(t -> taskStateMap.getOrDefault(t.getId(), TaskState.BACKLOG.name()))
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        Map<String, Long> byPriority = tasks.stream()
                .filter(t -> t.getPriority() != null)
                .collect(Collectors.groupingBy(Task::getPriority, Collectors.counting()));

        return TaskStatsDto.builder()
                .total(tasks.size())
                .byState(byState)
                .byPriority(byPriority)
                .build();
    }
}
