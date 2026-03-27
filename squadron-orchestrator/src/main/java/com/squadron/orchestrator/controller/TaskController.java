package com.squadron.orchestrator.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.TaskStatsDto;
import com.squadron.orchestrator.dto.TaskSyncRequest;
import com.squadron.orchestrator.dto.TaskSyncResult;
import com.squadron.orchestrator.dto.TaskWorkflowDto;
import com.squadron.orchestrator.dto.TransitionRequest;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.service.TaskService;
import com.squadron.orchestrator.service.TaskSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Task management and lifecycle")
public class TaskController {

    private final TaskService taskService;
    private final TaskSyncService taskSyncService;

    public TaskController(TaskService taskService, TaskSyncService taskSyncService) {
        this.taskService = taskService;
        this.taskSyncService = taskSyncService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    @Operation(summary = "Create a new task", description = "Creates a task and initializes its workflow")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Task created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Task>> createTask(@Valid @RequestBody CreateTaskRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Task task = taskService.createTask(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(task));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    @Operation(summary = "Get a task by ID")
    public ResponseEntity<ApiResponse<Task>> getTask(@PathVariable UUID id) {
        Task task = taskService.getTask(id);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<Task>>> listByProject(@PathVariable UUID projectId) {
        List<Task> tasks = taskService.listTasksByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/team/{teamId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<Task>>> listByTeam(@PathVariable UUID teamId) {
        List<Task> tasks = taskService.listTasksByTeam(teamId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/assignee/{assigneeId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<Task>>> listByAssignee(@PathVariable UUID assigneeId) {
        List<Task> tasks = taskService.listTasksByAssignee(assigneeId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    @Operation(summary = "Update an existing task")
    public ResponseEntity<ApiResponse<Task>> updateTask(@PathVariable UUID id,
                                                         @Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.updateTask(id, request);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    @Operation(summary = "Delete a task")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    @Operation(summary = "Transition a task to a new state", description = "Advances the task through its workflow state machine")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transition successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<ApiResponse<TaskWorkflow>> transitionTask(@PathVariable UUID id,
                                                                     @Valid @RequestBody TransitionRequest request,
                                                                     @AuthenticationPrincipal Jwt jwt) {
        request.setTaskId(id);
        UUID userId = UUID.fromString(jwt.getSubject());
        TaskWorkflow workflow = taskService.transitionTask(request, userId);
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }

    @GetMapping("/{id}/workflow")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<TaskWorkflowDto>> getTaskWorkflow(@PathVariable UUID id) {
        TaskWorkflowDto workflow = taskService.getTaskWorkflow(id);
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<TaskStateHistory>>> getTaskHistory(@PathVariable UUID id) {
        List<TaskStateHistory> history = taskService.getTaskHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableTransitions(@PathVariable UUID id) {
        List<String> transitions = taskService.getAvailableTransitions(id);
        return ResponseEntity.ok(ApiResponse.success(transitions));
    }

    @GetMapping("/by-state")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<Map<String, List<Task>>>> getTasksByState(
            @RequestParam UUID tenantId) {
        Map<String, List<Task>> tasksByState = taskService.getTasksByState(tenantId);
        return ResponseEntity.ok(ApiResponse.success(tasksByState));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<TaskStatsDto>> getTaskStats(
            @RequestParam UUID tenantId) {
        TaskStatsDto stats = taskService.getTaskStats(tenantId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    @Operation(summary = "Sync tasks from external platform", description = "Imports tasks from connected ticketing platform")
    public ResponseEntity<ApiResponse<TaskSyncResult>> syncTasks(@Valid @RequestBody TaskSyncRequest request) {
        TaskSyncResult result = taskSyncService.syncTasks(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
