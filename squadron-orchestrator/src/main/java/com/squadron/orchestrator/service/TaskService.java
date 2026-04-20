package com.squadron.orchestrator.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.common.event.TicketlessTaskCreatedEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.CreateTicketlessTaskRequest;
import com.squadron.orchestrator.dto.DelegateTaskRequest;
import com.squadron.orchestrator.dto.TaskDetailDto;
import com.squadron.orchestrator.dto.TaskStatsDto;
import com.squadron.orchestrator.dto.TaskWorkflowDto;
import com.squadron.orchestrator.dto.TransitionRequest;
import com.squadron.orchestrator.engine.TaskState;
import com.squadron.orchestrator.engine.TicketlessStatus;
import com.squadron.orchestrator.engine.WorkflowEngine;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.repository.ProjectRepository;
import com.squadron.orchestrator.repository.ProjectWorkflowMappingRepository;
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
import com.squadron.common.security.TenantScopedLookup;

@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskWorkflowRepository taskWorkflowRepository;
    private final TaskStateHistoryRepository taskStateHistoryRepository;
    private final WorkflowEngine workflowEngine;
    private final ProjectRepository projectRepository;
    private final ProjectWorkflowMappingRepository mappingRepository;
    private final NatsEventPublisher natsEventPublisher;

    public TaskService(TaskRepository taskRepository,
                       TaskWorkflowRepository taskWorkflowRepository,
                       TaskStateHistoryRepository taskStateHistoryRepository,
                       WorkflowEngine workflowEngine,
                       ProjectRepository projectRepository,
                       ProjectWorkflowMappingRepository mappingRepository,
                       NatsEventPublisher natsEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskWorkflowRepository = taskWorkflowRepository;
        this.taskStateHistoryRepository = taskStateHistoryRepository;
        this.workflowEngine = workflowEngine;
        this.projectRepository = projectRepository;
        this.mappingRepository = mappingRepository;
        this.natsEventPublisher = natsEventPublisher;
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
        return TenantScopedLookup.findByIdScoped(id, taskRepository::findById, taskRepository::findByIdAndTenantId, () -> new ResourceNotFoundException("Task", id));
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

        // Group tasks by their workflow state (exclude ticketless tasks — they have their own column)
        Map<String, List<Task>> result = new LinkedHashMap<>();
        for (TaskState state : TaskState.values()) {
            result.put(state.name(), new ArrayList<>());
        }

        for (Task task : tasks) {
            if (Boolean.TRUE.equals(task.getTicketless())) {
                continue; // Ticketless tasks are returned by getTicketlessTasks()
            }
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

    @Transactional(readOnly = true)
    public TaskDetailDto getTaskDetail(UUID taskId) {
        Task task = TenantScopedLookup.findByIdScoped(taskId, taskRepository::findById, taskRepository::findByIdAndTenantId, () -> new ResourceNotFoundException("Task", taskId));

        // Get workflow state
        TaskWorkflow workflow = taskWorkflowRepository.findByTaskId(taskId).orElse(null);

        // Get project name and mapped external status
        String projectName = null;
        String mappedExternalStatus = null;
        if (task.getProjectId() != null) {
            Project project = projectRepository.findByIdAndTenantId(task.getProjectId(), task.getTenantId()).orElse(null);
            if (project != null) {
                projectName = project.getName();
                // Look up workflow mapping for current state
                if (workflow != null) {
                    List<ProjectWorkflowMapping> mappings = mappingRepository.findByProjectId(project.getId());
                    mappedExternalStatus = mappings.stream()
                            .filter(m -> m.getInternalState().equals(workflow.getCurrentState()))
                            .map(ProjectWorkflowMapping::getExternalStatus)
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        // Get available transitions
        List<String> availableTransitions = List.of();
        if (workflow != null) {
            try {
                availableTransitions = workflowEngine.getAvailableTransitions(taskId);
            } catch (Exception e) {
                log.debug("Could not get available transitions for task {}: {}", taskId, e.getMessage());
            }
        }

        // Parse labels from JSON string to list
        List<String> labelsList = List.of();
        if (task.getLabels() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                labelsList = mapper.readValue(task.getLabels(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                log.debug("Could not parse labels for task {}: {}", taskId, e.getMessage());
            }
        }

        return TaskDetailDto.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .projectId(task.getProjectId())
                .teamId(task.getTeamId())
                .assigneeId(task.getAssigneeId())
                .title(task.getTitle())
                .description(task.getDescription())
                .externalId(task.getExternalId())
                .externalUrl(task.getExternalUrl())
                .priority(task.getPriority())
                .labels(labelsList)
                .tokenUsage(task.getTokenUsage() != null ? task.getTokenUsage() : 0L)
                .currentState(workflow != null ? workflow.getCurrentState() : null)
                .previousState(workflow != null ? workflow.getPreviousState() : null)
                .lastTransitionAt(workflow != null && workflow.getTransitionAt() != null ? workflow.getTransitionAt().toString() : null)
                .availableTransitions(availableTransitions)
                .projectName(projectName)
                .mappedExternalStatus(mappedExternalStatus)
                .createdAt(task.getCreatedAt() != null ? task.getCreatedAt().toString() : null)
                .updatedAt(task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : null)
                .build();
    }

    // --- Ticketless task methods ---

    /**
     * Creates a ticketless task and publishes a NATS event to trigger agent execution.
     */
    public Task createTicketlessTask(CreateTicketlessTaskRequest request) {
        log.info("Creating ticketless task for tenant {} with mode {}", request.getTenantId(), request.getAgentMode());

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            // Auto-generate title from prompt (first 80 chars)
            title = request.getPrompt().length() > 80
                    ? request.getPrompt().substring(0, 80) + "..."
                    : request.getPrompt();
        }

        Task task = Task.builder()
                .tenantId(request.getTenantId())
                .projectId(request.getProjectId())
                .title(title)
                .description(request.getPrompt())
                .priority(request.getPriority())
                .ticketless(true)
                .ticketlessStatus(TicketlessStatus.CREATED.name())
                .branchName(request.getBranchName())
                .createBranch(request.isCreateBranch())
                .agentMode(request.getAgentMode())
                .agentConfigId(request.getAgentConfigId())
                .prompt(request.getPrompt())
                .build();

        task = taskRepository.save(task);

        // Publish NATS event for agent module to pick up
        TicketlessTaskCreatedEvent event = new TicketlessTaskCreatedEvent();
        event.setTenantId(request.getTenantId());
        event.setSource("squadron-orchestrator");
        event.setTaskId(task.getId());
        event.setPrompt(request.getPrompt());
        event.setBranchName(request.getBranchName());
        event.setCreateBranch(request.isCreateBranch());
        event.setAgentMode(request.getAgentMode());
        event.setAgentConfigId(request.getAgentConfigId());
        event.setProjectId(request.getProjectId());

        try {
            natsEventPublisher.publishAsync("squadron.tasks.ticketless.created", event);
            log.info("Published ticketless task created event for task {}", task.getId());
        } catch (Exception e) {
            log.warn("Failed to publish ticketless task event for task {}: {}", task.getId(), e.getMessage());
        }

        return task;
    }

    @Transactional(readOnly = true)
    public List<Task> getTicketlessTasks(UUID tenantId) {
        return taskRepository.findByTenantIdAndTicketlessTrue(tenantId);
    }

    public Task updateTicketlessStatus(UUID taskId, String status) {
        Task task = getTask(taskId);
        if (!Boolean.TRUE.equals(task.getTicketless())) {
            throw new IllegalArgumentException("Task " + taskId + " is not a ticketless task");
        }
        // Validate status
        TicketlessStatus.valueOf(status);
        task.setTicketlessStatus(status);
        return taskRepository.save(task);
    }

    public void delegateToAgent(UUID taskId, DelegateTaskRequest request) {
        Task task = TenantScopedLookup.findByIdScoped(taskId, taskRepository::findById, taskRepository::findByIdAndTenantId, () -> new ResourceNotFoundException("Task", taskId));

        // If a target state is specified, transition the task first
        if (request.getTargetState() != null && !request.getTargetState().isBlank()) {
            workflowEngine.transition(taskId, request.getTargetState(), null, "Delegated to agent: " + request.getAgentType());
        }

        // Publish a NATS event to trigger the agent
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTenantId(task.getTenantId());
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState(null);
        event.setToState(request.getTargetState() != null ? request.getTargetState() : "PLANNING");
        event.setReason("Delegated to agent: " + request.getAgentType());

        try {
            natsEventPublisher.publishAsync("squadron.tasks.state-changed", event);
        } catch (Exception e) {
            log.warn("Failed to publish delegate event for task {}: {}", taskId, e.getMessage());
        }
    }
}
