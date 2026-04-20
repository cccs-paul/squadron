package com.squadron.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.security.TenantContext;
import com.squadron.orchestrator.config.SecurityConfig;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.CreateTicketlessTaskRequest;
import com.squadron.orchestrator.dto.DelegateTaskRequest;
import com.squadron.orchestrator.dto.TaskDetailDto;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TaskController.class)
@ContextConfiguration(classes = {TaskController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @MockBean
    private TaskSyncService taskSyncService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_createTask_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Fix bug")
                .build();

        Task savedTask = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Fix bug")
                .build();

        when(taskService.createTask(any(CreateTaskRequest.class), any(UUID.class)))
                .thenReturn(savedTask);

        mockMvc.perform(post("/api/tasks")
                        .with(jwt().jwt(j -> j.subject(userId.toString())
                                .claim("roles", List.of("squadron-admin")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_squadron-admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Fix bug"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTask_when_exists() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .title("Found Task")
                .build();

        when(taskService.getTask(taskId)).thenReturn(task);

        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Found Task"));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_listByProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).projectId(projectId).title("T1").build()
        );

        when(taskService.listTasksByProject(projectId)).thenReturn(tasks);

        mockMvc.perform(get("/api/tasks/project/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("T1"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_listByTeam() throws Exception {
        UUID teamId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).teamId(teamId).title("Team Task").build()
        );

        when(taskService.listTasksByTeam(teamId)).thenReturn(tasks);

        mockMvc.perform(get("/api/tasks/team/{teamId}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Team Task"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_listByAssignee() throws Exception {
        UUID assigneeId = UUID.randomUUID();
        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).assigneeId(assigneeId).title("My Task").build()
        );

        when(taskService.listTasksByAssignee(assigneeId)).thenReturn(tasks);

        mockMvc.perform(get("/api/tasks/assignee/{assigneeId}", assigneeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("My Task"));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_updateTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Updated Task")
                .build();

        Task updated = Task.builder()
                .id(taskId)
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title("Updated Task")
                .build();

        when(taskService.updateTask(eq(taskId), any(CreateTaskRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/tasks/{id}", taskId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Updated Task"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_deleteTask() throws Exception {
        UUID taskId = UUID.randomUUID();

        doNothing().when(taskService).deleteTask(taskId);

        mockMvc.perform(delete("/api/tasks/{id}", taskId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(taskId);
    }

    @Test
    void should_transitionTask() throws Exception {
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

        when(taskService.transitionTask(any(TransitionRequest.class), any(UUID.class)))
                .thenReturn(workflow);

        mockMvc.perform(post("/api/tasks/{id}/transition", taskId)
                        .with(jwt().jwt(j -> j.subject(userId.toString())
                                .claim("roles", List.of("developer")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_developer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentState").value("PLANNING"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_getTaskWorkflow() throws Exception {
        UUID taskId = UUID.randomUUID();

        TaskWorkflowDto dto = TaskWorkflowDto.builder()
                .taskId(taskId)
                .currentState("REVIEW")
                .previousState("PROPOSE_CODE")
                .transitionAt(Instant.now())
                .build();

        when(taskService.getTaskWorkflow(taskId)).thenReturn(dto);

        mockMvc.perform(get("/api/tasks/{id}/workflow", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentState").value("REVIEW"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTaskHistory() throws Exception {
        UUID taskId = UUID.randomUUID();

        List<TaskStateHistory> history = List.of(
                TaskStateHistory.builder()
                        .id(UUID.randomUUID())
                        .taskWorkflowId(UUID.randomUUID())
                        .fromState("BACKLOG")
                        .toState("PRIORITIZED")
                        .triggeredBy(UUID.randomUUID())
                        .reason("Sprint planned")
                        .build()
        );

        when(taskService.getTaskHistory(taskId)).thenReturn(history);

        mockMvc.perform(get("/api/tasks/{id}/history", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].toState").value("PRIORITIZED"));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_getAvailableTransitions() throws Exception {
        UUID taskId = UUID.randomUUID();

        when(taskService.getAvailableTransitions(taskId)).thenReturn(List.of("MERGE", "REVIEW"));

        mockMvc.perform(get("/api/tasks/{id}/transitions", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("MERGE"))
                .andExpect(jsonPath("$.data[1]").value("REVIEW"));
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToCreate() throws Exception {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .title("Test")
                .build();

        mockMvc.perform(post("/api/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToDelete() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTasksByState_when_tasksExist() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder().tenantId(tenantId).build());

        Map<String, List<Task>> tasksByState = new LinkedHashMap<>();
        tasksByState.put("BACKLOG", List.of(
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("Backlog Task").build()
        ));
        tasksByState.put("PLANNING", List.of(
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("Planning Task").build()
        ));
        tasksByState.put("PRIORITIZED", Collections.emptyList());

        when(taskService.getTasksByState(tenantId)).thenReturn(tasksByState);

        mockMvc.perform(get("/api/tasks/by-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.BACKLOG[0].title").value("Backlog Task"))
                .andExpect(jsonPath("$.data.PLANNING[0].title").value("Planning Task"))
                .andExpect(jsonPath("$.data.PRIORITIZED").isEmpty());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_getTasksByState_when_noTasks() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder().tenantId(tenantId).build());

        Map<String, List<Task>> emptyByState = new LinkedHashMap<>();
        emptyByState.put("BACKLOG", Collections.emptyList());
        emptyByState.put("PRIORITIZED", Collections.emptyList());

        when(taskService.getTasksByState(tenantId)).thenReturn(emptyByState);

        mockMvc.perform(get("/api/tasks/by-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.BACKLOG").isEmpty())
                .andExpect(jsonPath("$.data.PRIORITIZED").isEmpty());
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_getTaskStats_when_tasksExist() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder().tenantId(tenantId).build());

        TaskStatsDto stats = TaskStatsDto.builder()
                .total(5)
                .byState(Map.of("BACKLOG", 2L, "PLANNING", 3L))
                .byPriority(Map.of("HIGH", 3L, "LOW", 2L))
                .build();

        when(taskService.getTaskStats(tenantId)).thenReturn(stats);

        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.byState.BACKLOG").value(2))
                .andExpect(jsonPath("$.data.byState.PLANNING").value(3))
                .andExpect(jsonPath("$.data.byPriority.HIGH").value(3))
                .andExpect(jsonPath("$.data.byPriority.LOW").value(2));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_getTaskStats_when_noTasksWithPriority() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder().tenantId(tenantId).build());

        TaskStatsDto stats = TaskStatsDto.builder()
                .total(2)
                .byState(Map.of("BACKLOG", 2L))
                .byPriority(Collections.emptyMap())
                .build();

        when(taskService.getTaskStats(tenantId)).thenReturn(stats);

        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.byState.BACKLOG").value(2))
                .andExpect(jsonPath("$.data.byPriority").isEmpty());
    }

    @Test
    void should_syncTasks_when_developerRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskSyncRequest request = TaskSyncRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .platformConnectionId(connectionId)
                .projectKey("PROJ")
                .build();

        TaskSyncResult result = TaskSyncResult.builder()
                .created(3)
                .updated(1)
                .unchanged(2)
                .failed(0)
                .errors(Collections.emptyList())
                .build();

        when(taskSyncService.syncTasks(any(TaskSyncRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/tasks/sync")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("developer")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_developer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.updated").value(1));
    }

    @Test
    void should_syncTasks_when_teamLeadRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskSyncRequest request = TaskSyncRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .platformConnectionId(connectionId)
                .projectKey("PROJ")
                .build();

        TaskSyncResult result = TaskSyncResult.builder()
                .created(0)
                .updated(0)
                .unchanged(5)
                .failed(0)
                .errors(Collections.emptyList())
                .build();

        when(taskSyncService.syncTasks(any(TaskSyncRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/tasks/sync")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("team-lead")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_team-lead")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unchanged").value(5));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToSync() throws Exception {
        TaskSyncRequest request = TaskSyncRequest.builder()
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .platformConnectionId(UUID.randomUUID())
                .projectKey("PROJ")
                .build();

        mockMvc.perform(post("/api/tasks/sync")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- Task Detail Tests ---

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTaskDetail_when_authenticated() throws Exception {
        UUID taskId = UUID.randomUUID();

        TaskDetailDto detail = TaskDetailDto.builder()
            .id(taskId)
            .title("Bug Fix")
            .currentState("REVIEW")
            .previousState("PROPOSE_CODE")
            .projectName("My Project")
            .mappedExternalStatus("In Review")
            .availableTransitions(List.of("QA", "MERGE"))
            .labels(List.of("bug"))
            .tokenUsage(500)
            .build();

        when(taskService.getTaskDetail(taskId)).thenReturn(detail);

        mockMvc.perform(get("/api/tasks/{id}/detail", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Bug Fix"))
            .andExpect(jsonPath("$.data.currentState").value("REVIEW"))
            .andExpect(jsonPath("$.data.projectName").value("My Project"))
            .andExpect(jsonPath("$.data.mappedExternalStatus").value("In Review"))
            .andExpect(jsonPath("$.data.availableTransitions[0]").value("QA"))
            .andExpect(jsonPath("$.data.labels[0]").value("bug"))
            .andExpect(jsonPath("$.data.tokenUsage").value(500));
    }

    // --- Delegate to Agent Tests ---

    @Test
    void should_delegateToAgent_when_developerRole() throws Exception {
        UUID taskId = UUID.randomUUID();

        DelegateTaskRequest request = DelegateTaskRequest.builder()
            .agentType("CODING")
            .targetState("PROPOSE_CODE")
            .instructions("Focus on the login module")
            .build();

        doNothing().when(taskService).delegateToAgent(eq(taskId), any(DelegateTaskRequest.class));

        mockMvc.perform(post("/api/tasks/{id}/delegate", taskId)
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                    .claim("roles", List.of("developer")))
                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_developer")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(taskService).delegateToAgent(eq(taskId), any(DelegateTaskRequest.class));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToDelegate() throws Exception {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
            .agentType("PLANNING")
            .build();

        mockMvc.perform(post("/api/tasks/{id}/delegate", UUID.randomUUID())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_when_delegatingUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/tasks/{id}/delegate", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentType\":\"CODING\"}"))
            .andExpect(status().isUnauthorized());
    }

    // --- Ticketless task tests ---

    @Test
    void should_createTicketlessTask_when_developerRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID agentConfigId = UUID.randomUUID();

        CreateTicketlessTaskRequest request = CreateTicketlessTaskRequest.builder()
                .tenantId(tenantId)
                .prompt("Implement login page")
                .branchName("feature/login")
                .createBranch(true)
                .agentMode("BUILD")
                .agentConfigId(agentConfigId)
                .build();

        Task savedTask = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .title("Implement login page")
                .ticketless(true)
                .ticketlessStatus("CREATED")
                .branchName("feature/login")
                .agentMode("BUILD")
                .agentConfigId(agentConfigId)
                .build();

        when(taskService.createTicketlessTask(any(CreateTicketlessTaskRequest.class)))
                .thenReturn(savedTask);

        mockMvc.perform(post("/api/tasks/ticketless")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("developer"))
                                .claim("tenant_id", tenantId.toString()))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_developer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketless").value(true))
                .andExpect(jsonPath("$.data.ticketlessStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.branchName").value("feature/login"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getTicketlessTasks_when_authenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder().tenantId(tenantId).build());

        List<Task> tasks = List.of(
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("T1")
                        .ticketless(true).ticketlessStatus("CREATED").build(),
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("T2")
                        .ticketless(true).ticketlessStatus("COMPLETED").build()
        );

        when(taskService.getTicketlessTasks(tenantId)).thenReturn(tasks);

        mockMvc.perform(get("/api/tasks/ticketless"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ticketless").value(true));
    }

    @Test
    void should_updateTicketlessStatus_when_developerRole() throws Exception {
        UUID taskId = UUID.randomUUID();

        Task updatedTask = Task.builder()
                .id(taskId)
                .ticketless(true)
                .ticketlessStatus("BUILDING")
                .title("Ticketless Task")
                .build();

        when(taskService.updateTicketlessStatus(eq(taskId), eq("BUILDING")))
                .thenReturn(updatedTask);

        mockMvc.perform(put("/api/tasks/{id}/ticketless-status", taskId)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("developer")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_developer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUILDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketlessStatus").value("BUILDING"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToCreateTicketless() throws Exception {
        CreateTicketlessTaskRequest request = CreateTicketlessTaskRequest.builder()
                .tenantId(UUID.randomUUID())
                .prompt("test")
                .branchName("main")
                .agentMode("PLAN")
                .agentConfigId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/tasks/ticketless")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
