package com.squadron.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.dto.TaskSyncRequest;
import com.squadron.orchestrator.dto.TaskSyncResult;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskSyncServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> headersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private TaskSyncService taskSyncService;

    private UUID tenantId;
    private UUID teamId;
    private UUID projectId;
    private UUID connectionId;
    private TaskSyncRequest syncRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        taskSyncService = new TaskSyncService(taskRepository, restClient, objectMapper);

        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        connectionId = UUID.randomUUID();

        syncRequest = TaskSyncRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .platformConnectionId(connectionId)
                .projectKey("PROJ")
                .build();
    }

    @SuppressWarnings("unchecked")
    private void setupRestClientMock(Map<String, Object> response) {
        RestClient.RequestHeadersUriSpec rawUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec rawHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec rawResponseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(rawUriSpec);
        when(rawUriSpec.uri(anyString(), any(), any())).thenReturn(rawHeadersSpec);
        when(rawHeadersSpec.retrieve()).thenReturn(rawResponseSpec);
        when(rawResponseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void setupRestClientMockThrows(RuntimeException exception) {
        RestClient.RequestHeadersUriSpec rawUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec rawHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec rawResponseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(rawUriSpec);
        when(rawUriSpec.uri(anyString(), any(), any())).thenReturn(rawHeadersSpec);
        when(rawHeadersSpec.retrieve()).thenReturn(rawResponseSpec);
        when(rawResponseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(exception);
    }

    private Map<String, Object> buildExternalTask(String externalId, String title,
                                                    String description, String priority,
                                                    List<String> labels) {
        Map<String, Object> task = new HashMap<>();
        task.put("externalId", externalId);
        task.put("externalUrl", "https://jira.example.com/browse/" + externalId);
        task.put("title", title);
        task.put("description", description);
        task.put("priority", priority);
        task.put("labels", labels);
        return task;
    }

    private Map<String, Object> buildApiResponse(List<Map<String, Object>> tasks) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", tasks);
        return response;
    }

    @Test
    void should_createNewTasks_when_noExistingTasks() {
        Map<String, Object> extTask1 = buildExternalTask("PROJ-1", "Fix login bug",
                "Users cannot login", "HIGH", List.of("bug", "urgent"));
        Map<String, Object> extTask2 = buildExternalTask("PROJ-2", "Add dashboard",
                "New feature", "MEDIUM", List.of("feature"));

        Map<String, Object> apiResponse = buildApiResponse(List.of(extTask1, extTask2));
        setupRestClientMock(apiResponse);

        when(taskRepository.findByExternalId("PROJ-1")).thenReturn(Optional.empty());
        when(taskRepository.findByExternalId("PROJ-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(2, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertTrue(result.getErrors().isEmpty());
        verify(taskRepository, times(2)).save(any(Task.class));
    }

    @Test
    void should_updateExistingTasks_when_titleChanged() {
        Map<String, Object> extTask = buildExternalTask("PROJ-1", "Updated title",
                "Same description", "HIGH", List.of("bug"));

        Map<String, Object> apiResponse = buildApiResponse(List.of(extTask));
        setupRestClientMock(apiResponse);

        Task existingTask = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("PROJ-1")
                .externalUrl("https://jira.example.com/browse/PROJ-1")
                .title("Old title")
                .description("Same description")
                .priority("HIGH")
                .labels("[\"bug\"]")
                .build();

        when(taskRepository.findByExternalId("PROJ-1")).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(0, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertEquals("Updated title", existingTask.getTitle());
        verify(taskRepository, times(1)).save(existingTask);
    }

    @Test
    void should_markUnchanged_when_noFieldsDiffer() throws Exception {
        String labelsJson = objectMapper.writeValueAsString(List.of("bug"));

        Map<String, Object> extTask = buildExternalTask("PROJ-1", "Same title",
                "Same description", "HIGH", List.of("bug"));

        Map<String, Object> apiResponse = buildApiResponse(List.of(extTask));
        setupRestClientMock(apiResponse);

        Task existingTask = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("PROJ-1")
                .externalUrl("https://jira.example.com/browse/PROJ-1")
                .title("Same title")
                .description("Same description")
                .priority("HIGH")
                .labels(labelsJson)
                .build();

        when(taskRepository.findByExternalId("PROJ-1")).thenReturn(Optional.of(existingTask));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(1, result.getUnchanged());
        assertEquals(0, result.getFailed());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void should_handleEmptyResponse_when_platformReturnsNoTasks() {
        Map<String, Object> apiResponse = buildApiResponse(List.of());
        setupRestClientMock(apiResponse);

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertTrue(result.getErrors().isEmpty());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void should_handleNullResponse_when_platformReturnsNull() {
        setupRestClientMock(null);

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertTrue(result.getErrors().isEmpty());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void should_countFailures_when_individualTaskFails() {
        Map<String, Object> extTask1 = buildExternalTask("PROJ-1", "Good task",
                "Description", "HIGH", List.of("bug"));
        Map<String, Object> extTask2 = buildExternalTask("PROJ-2", "Bad task",
                "Description", "LOW", List.of("feature"));

        Map<String, Object> apiResponse = buildApiResponse(List.of(extTask1, extTask2));
        setupRestClientMock(apiResponse);

        when(taskRepository.findByExternalId("PROJ-1")).thenReturn(Optional.empty());
        when(taskRepository.findByExternalId("PROJ-2")).thenThrow(new RuntimeException("DB connection lost"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(1, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("DB connection lost"));
    }

    @Test
    void should_handlePlatformServiceError_when_restCallFails() {
        setupRestClientMockThrows(new RestClientException("Connection refused"));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(0, result.getCreated());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getUnchanged());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("Connection refused"));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void should_syncMultipleTasks_when_mixOfNewAndExisting() throws Exception {
        String labelsJson = objectMapper.writeValueAsString(List.of("existing"));

        Map<String, Object> newTask = buildExternalTask("PROJ-1", "New task",
                "Brand new", "HIGH", List.of("feature"));
        Map<String, Object> updatedTask = buildExternalTask("PROJ-2", "Updated task",
                "Changed desc", "MEDIUM", List.of("bug"));
        Map<String, Object> unchangedTask = buildExternalTask("PROJ-3", "Same task",
                "Same desc", "LOW", List.of("existing"));

        Map<String, Object> apiResponse = buildApiResponse(List.of(newTask, updatedTask, unchangedTask));
        setupRestClientMock(apiResponse);

        // PROJ-1: not found -> create
        when(taskRepository.findByExternalId("PROJ-1")).thenReturn(Optional.empty());

        // PROJ-2: found with different title -> update
        Task existingTask2 = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("PROJ-2")
                .externalUrl("https://jira.example.com/browse/PROJ-2")
                .title("Old title")
                .description("Old desc")
                .priority("MEDIUM")
                .labels("[\"bug\"]")
                .build();
        when(taskRepository.findByExternalId("PROJ-2")).thenReturn(Optional.of(existingTask2));

        // PROJ-3: found with same data -> unchanged
        Task existingTask3 = Task.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .externalId("PROJ-3")
                .externalUrl("https://jira.example.com/browse/PROJ-3")
                .title("Same task")
                .description("Same desc")
                .priority("LOW")
                .labels(labelsJson)
                .build();
        when(taskRepository.findByExternalId("PROJ-3")).thenReturn(Optional.of(existingTask3));

        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskSyncResult result = taskSyncService.syncTasks(syncRequest);

        assertNotNull(result);
        assertEquals(1, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getUnchanged());
        assertEquals(0, result.getFailed());
        assertTrue(result.getErrors().isEmpty());

        // 2 saves: 1 for new task, 1 for updated task (unchanged is not saved)
        verify(taskRepository, times(2)).save(any(Task.class));
    }
}
