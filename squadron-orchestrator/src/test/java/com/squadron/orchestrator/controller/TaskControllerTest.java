package com.squadron.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.config.SecurityConfig;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.TaskStatsDto;
import com.squadron.orchestrator.dto.TaskWorkflowDto;
import com.squadron.orchestrator.dto.TransitionRequest;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.service.TaskService;
import com.squadron.orchestrator.service.TaskSyncService;
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

        Map<String, List<Task>> tasksByState = new LinkedHashMap<>();
        tasksByState.put("BACKLOG", List.of(
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("Backlog Task").build()
        ));
        tasksByState.put("PLANNING", List.of(
                Task.builder().id(UUID.randomUUID()).tenantId(tenantId).title("Planning Task").build()
        ));
        tasksByState.put("PRIORITIZED", Collections.emptyList());

        when(taskService.getTasksByState(tenantId)).thenReturn(tasksByState);

        mockMvc.perform(get("/api/tasks/by-state")
                        .param("tenantId", tenantId.toString()))
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

        Map<String, List<Task>> emptyByState = new LinkedHashMap<>();
        emptyByState.put("BACKLOG", Collections.emptyList());
        emptyByState.put("PRIORITIZED", Collections.emptyList());

        when(taskService.getTasksByState(tenantId)).thenReturn(emptyByState);

        mockMvc.perform(get("/api/tasks/by-state")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.BACKLOG").isEmpty())
                .andExpect(jsonPath("$.data.PRIORITIZED").isEmpty());
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_getTaskStats_when_tasksExist() throws Exception {
        UUID tenantId = UUID.randomUUID();

        TaskStatsDto stats = TaskStatsDto.builder()
                .total(5)
                .byState(Map.of("BACKLOG", 2L, "PLANNING", 3L))
                .byPriority(Map.of("HIGH", 3L, "LOW", 2L))
                .build();

        when(taskService.getTaskStats(tenantId)).thenReturn(stats);

        mockMvc.perform(get("/api/tasks/stats")
                        .param("tenantId", tenantId.toString()))
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

        TaskStatsDto stats = TaskStatsDto.builder()
                .total(2)
                .byState(Map.of("BACKLOG", 2L))
                .byPriority(Collections.emptyMap())
                .build();

        when(taskService.getTaskStats(tenantId)).thenReturn(stats);

        mockMvc.perform(get("/api/tasks/stats")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.byState.BACKLOG").value(2))
                .andExpect(jsonPath("$.data.byPriority").isEmpty());
    }
}
