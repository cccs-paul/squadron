package com.squadron.platform.adapter.gitlab;

import com.squadron.platform.config.WebClientSslHelper;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitLabIssuesAdapterTest {

    @Mock
    private WebClientSslHelper sslHelper;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GitLabIssuesAdapter adapter;

    private static final String ISSUES_ARRAY_JSON = """
            [
              {
                "iid": 7,
                "web_url": "https://gitlab.com/org/repo/-/issues/7",
                "title": "Fix bug",
                "description": "Description text",
                "state": "opened",
                "assignee": {"username": "user1", "name": "User One"},
                "labels": ["bug", "urgent"],
                "created_at": "2024-01-01T00:00:00.000Z",
                "updated_at": "2024-01-02T00:00:00.000Z"
              },
              {
                "iid": 8,
                "web_url": "https://gitlab.com/org/repo/-/issues/8",
                "title": "Add feature",
                "description": "Feature description",
                "state": "closed",
                "assignee": null,
                "labels": [],
                "created_at": "2024-02-01T00:00:00.000Z",
                "updated_at": "2024-02-02T00:00:00.000Z"
              }
            ]
            """;

    private static final String SINGLE_ISSUE_JSON = """
            {
              "iid": 7,
              "web_url": "https://gitlab.com/org/repo/-/issues/7",
              "title": "Fix bug",
              "description": "Description text",
              "state": "opened",
              "assignee": {"username": "user1", "name": "User One"},
              "labels": ["bug", "priority::high"],
              "created_at": "2024-01-01T00:00:00.000Z",
              "updated_at": "2024-01-02T00:00:00.000Z"
            }
            """;

    @BeforeEach
    void setUp() {
        adapter = new GitLabIssuesAdapter(sslHelper);
    }

    private void configureAdapter() {
        when(sslHelper.trustedBuilder()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        adapter.configure("https://gitlab.com", Map.of("pat", "glpat-token"));
    }

    @SuppressWarnings("unchecked")
    private void mockGetReturning(String json) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));
    }

    @SuppressWarnings("unchecked")
    private void mockPutReturning(String json) {
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));
    }

    @SuppressWarnings("unchecked")
    private void mockPostReturning(String json) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(json));
    }

    // ========== Existing tests (preserved) ==========

    @Test
    void should_returnCorrectPlatformType() {
        assertEquals("GITLAB", adapter.getPlatformType());
    }

    @Test
    void should_configureAdapter() {
        when(sslHelper.trustedBuilder()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://gitlab.com", Map.of("pat", "glpat-token"));

        verify(webClientBuilder).baseUrl("https://gitlab.com/api/v4");
    }

    @Test
    void should_returnFalse_when_testConnectionNotConfigured() {
        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnTrue_when_testConnectionSucceeds() {
        configureAdapter();
        mockGetReturning("{\"id\":1,\"username\":\"user\"}");

        boolean result = adapter.testConnection();
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnFalse_when_testConnectionFails() {
        when(sslHelper.trustedBuilder()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://gitlab.com", Map.of("pat", "bad-token"));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("401")));

        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    @Test
    void should_returnAvailableStatuses() {
        List<String> statuses = adapter.getAvailableStatuses("12345");
        assertEquals(List.of("opened", "closed"), statuses);
    }

    // ========== fetchTasks tests ==========

    @Test
    void should_fetchTasks_when_validProjectAndFilter() {
        configureAdapter();
        mockGetReturning(ISSUES_ARRAY_JSON);

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .status("opened")
                .assignee("user1")
                .maxResults(25)
                .build();

        List<PlatformTaskDto> tasks = adapter.fetchTasks("42", filter);

        assertEquals(2, tasks.size());

        PlatformTaskDto first = tasks.get(0);
        assertEquals("42:7", first.getExternalId());
        assertEquals("https://gitlab.com/org/repo/-/issues/7", first.getExternalUrl());
        assertEquals("Fix bug", first.getTitle());
        assertEquals("Description text", first.getDescription());
        assertEquals("opened", first.getStatus());
        assertEquals("user1", first.getAssignee());
        assertEquals(List.of("bug", "urgent"), first.getLabels());
        assertNotNull(first.getCreatedAt());
        assertNotNull(first.getUpdatedAt());

        PlatformTaskDto second = tasks.get(1);
        assertEquals("42:8", second.getExternalId());
        assertEquals("Add feature", second.getTitle());
        assertEquals("closed", second.getStatus());
        assertNull(second.getAssignee());
        assertTrue(second.getLabels().isEmpty());

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestHeadersUriSpec).uri(uriCaptor.capture());
        String uri = uriCaptor.getValue();
        assertTrue(uri.contains("/projects/42/issues"));
        assertTrue(uri.contains("state=opened"));
        assertTrue(uri.contains("assignee_username=user1"));
        assertTrue(uri.contains("per_page=25"));
    }

    @Test
    void should_fetchTasks_when_nullFilter() {
        configureAdapter();
        mockGetReturning(ISSUES_ARRAY_JSON);

        List<PlatformTaskDto> tasks = adapter.fetchTasks("42", null);

        assertEquals(2, tasks.size());

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestHeadersUriSpec).uri(uriCaptor.capture());
        String uri = uriCaptor.getValue();
        assertTrue(uri.contains("per_page=50"));
        assertFalse(uri.contains("state="));
        assertFalse(uri.contains("assignee_username="));
    }

    // ========== getTask tests ==========

    @Test
    void should_getTask_when_validExternalId() {
        configureAdapter();
        mockGetReturning(SINGLE_ISSUE_JSON);

        PlatformTaskDto task = adapter.getTask("42:7");

        assertEquals("42:7", task.getExternalId());
        assertEquals("https://gitlab.com/org/repo/-/issues/7", task.getExternalUrl());
        assertEquals("Fix bug", task.getTitle());
        assertEquals("Description text", task.getDescription());
        assertEquals("opened", task.getStatus());
        assertEquals("high", task.getPriority());
        assertEquals("user1", task.getAssignee());
        assertEquals(List.of("bug", "priority::high"), task.getLabels());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestHeadersUriSpec).uri(uriCaptor.capture());
        assertEquals("/projects/42/issues/7", uriCaptor.getValue());
    }

    @Test
    void should_throwException_when_invalidExternalId() {
        assertThrows(IllegalArgumentException.class, () -> adapter.getTask("invalid"));
        assertThrows(IllegalArgumentException.class, () -> adapter.getTask(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.getTask(":7"));
        assertThrows(IllegalArgumentException.class, () -> adapter.getTask("42:"));
    }

    // ========== updateTaskStatus tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_toClose() {
        configureAdapter();
        mockPutReturning("{\"iid\":7,\"state\":\"closed\"}");

        adapter.updateTaskStatus("42:7", "closed", null);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertEquals("/projects/42/issues/7", uriCaptor.getValue());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        assertEquals("close", bodyCaptor.getValue().get("state_event"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_toReopen() {
        configureAdapter();
        mockPutReturning("{\"iid\":7,\"state\":\"opened\"}");

        adapter.updateTaskStatus("42:7", "opened", null);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        assertEquals("reopen", bodyCaptor.getValue().get("state_event"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_andAddComment_whenCommentProvided() {
        configureAdapter();
        mockPutReturning("{\"iid\":7,\"state\":\"closed\"}");
        mockPostReturning("{\"id\":1,\"body\":\"Done\"}");

        adapter.updateTaskStatus("42:7", "closed", "Done");

        verify(webClient).put();
        verify(webClient).post();
    }

    // ========== addComment tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void should_addComment_when_validExternalId() {
        configureAdapter();
        mockPostReturning("{\"id\":1,\"body\":\"Test comment\"}");

        adapter.addComment("42:7", "Test comment");

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertEquals("/projects/42/issues/7/notes", uriCaptor.getValue());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(bodyCaptor.capture());
        assertEquals("Test comment", bodyCaptor.getValue().get("body"));
    }

    // ========== Error handling tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_fetchTasksFails() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("500")));

        PlatformTaskFilter filter = PlatformTaskFilter.builder().build();
        assertThrows(RuntimeException.class, () -> adapter.fetchTasks("42", filter));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getTaskFails() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("404")));

        assertThrows(RuntimeException.class, () -> adapter.getTask("42:7"));
    }

    // ========== parseExternalId tests ==========

    @Test
    void should_parseExternalId_correctly() {
        String[] parts = adapter.parseExternalId("42:7");
        assertEquals("42", parts[0]);
        assertEquals("7", parts[1]);
    }

    @Test
    void should_parseExternalId_withColonInValue() {
        String[] parts = adapter.parseExternalId("42:7:extra");
        assertEquals("42", parts[0]);
        assertEquals("7:extra", parts[1]);
    }

    // ========== Priority extraction tests ==========

    @Test
    void should_extractPriority_fromPriorityLabel() {
        configureAdapter();
        String json = """
                {
                  "iid": 1,
                  "web_url": "https://gitlab.com/org/repo/-/issues/1",
                  "title": "Test",
                  "description": null,
                  "state": "opened",
                  "assignee": null,
                  "labels": ["bug", "priority::critical"],
                  "created_at": "2024-01-01T00:00:00.000Z",
                  "updated_at": "2024-01-01T00:00:00.000Z"
                }
                """;
        mockGetReturning(json);

        PlatformTaskDto task = adapter.getTask("10:1");
        assertEquals("critical", task.getPriority());
    }

    @Test
    void should_setNullPriority_whenNoPriorityLabel() {
        configureAdapter();
        String json = """
                {
                  "iid": 1,
                  "web_url": "https://gitlab.com/org/repo/-/issues/1",
                  "title": "Test",
                  "description": null,
                  "state": "opened",
                  "assignee": null,
                  "labels": ["bug", "enhancement"],
                  "created_at": "2024-01-01T00:00:00.000Z",
                  "updated_at": "2024-01-01T00:00:00.000Z"
                }
                """;
        mockGetReturning(json);

        PlatformTaskDto task = adapter.getTask("10:1");
        assertNull(task.getPriority());
    }

    // ========== getProjects tests ==========

    @Test
    void should_getProjects_when_configured() {
        configureAdapter();

        String json = """
                [
                  {
                    "id": 42,
                    "name": "My Project",
                    "description": "A GitLab project",
                    "web_url": "https://gitlab.com/mygroup/myproject",
                    "avatar_url": "https://gitlab.com/uploads/avatar.png"
                  },
                  {
                    "id": 99,
                    "name": "Another Project",
                    "description": null,
                    "web_url": "https://gitlab.com/mygroup/another",
                    "avatar_url": null
                  }
                ]
                """;
        mockGetReturning(json);

        var projects = adapter.getProjects();

        assertNotNull(projects);
        assertEquals(2, projects.size());

        assertEquals("42", projects.get(0).getKey());
        assertEquals("My Project", projects.get(0).getName());
        assertEquals("A GitLab project", projects.get(0).getDescription());
        assertEquals("https://gitlab.com/mygroup/myproject", projects.get(0).getUrl());
        assertEquals("https://gitlab.com/uploads/avatar.png", projects.get(0).getAvatarUrl());

        assertEquals("99", projects.get(1).getKey());
        assertEquals("Another Project", projects.get(1).getName());
        assertNull(projects.get(1).getDescription());
        assertNull(projects.get(1).getAvatarUrl());
    }

    @Test
    void should_returnEmptyProjects_when_noProjects() {
        configureAdapter();
        mockGetReturning("[]");

        var projects = adapter.getProjects();
        assertNotNull(projects);
        assertTrue(projects.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getProjectsFails() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("500")));

        assertThrows(RuntimeException.class, () -> adapter.getProjects());
    }

    @SuppressWarnings("unchecked")
    private void setupGetMock() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getProjectsReceivesHtmlResponse() {
        configureAdapter();
        setupGetMock();
        String htmlResponse = "<html><body><h1>Login Required</h1></body></html>";
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(htmlResponse));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> adapter.getProjects());
        assertTrue(ex.getMessage().contains("Received HTML instead of JSON"));
    }
}
