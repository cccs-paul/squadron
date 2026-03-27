package com.squadron.platform.adapter.jira;

import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraCloudAdapterTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    private JiraCloudAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JiraCloudAdapter(webClientBuilder);
    }

    private void configureAdapter() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        adapter.configure("https://example.atlassian.net", "my-token");
    }

    @SuppressWarnings("unchecked")
    private void setupGetMock() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @SuppressWarnings("unchecked")
    private void setupPostMock() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    // --- Platform type ---

    @Test
    void should_returnCorrectPlatformType() {
        assertEquals("JIRA_CLOUD", adapter.getPlatformType());
    }

    // --- Configure ---

    @Test
    void should_configureAdapter() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://example.atlassian.net", "my-token");

        verify(webClientBuilder).baseUrl("https://example.atlassian.net/rest/api/3");
    }

    // --- Test connection ---

    @Test
    void should_returnFalse_when_testConnectionNotConfigured() {
        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnTrue_when_testConnectionSucceeds() {
        configureAdapter();
        setupGetMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"accountId\":\"123\"}"));

        boolean result = adapter.testConnection();
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnFalse_when_testConnectionFails() {
        configureAdapter();
        setupGetMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("401 Unauthorized")));

        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    // --- fetchTasks ---

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchTasks_when_validProjectKey() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = """
                {
                  "issues": [
                    {
                      "key": "PROJ-1",
                      "self": "https://example.atlassian.net/rest/api/3/issue/10001",
                      "fields": {
                        "summary": "Fix the login bug",
                        "description": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Bug description"}]}]},
                        "status": {"name": "In Progress"},
                        "priority": {"name": "High"},
                        "assignee": {"displayName": "John Doe"},
                        "labels": ["bug", "urgent"],
                        "created": "2024-01-01T00:00:00.000+0000",
                        "updated": "2024-01-02T00:00:00.000+0000"
                      }
                    },
                    {
                      "key": "PROJ-2",
                      "self": "https://example.atlassian.net/rest/api/3/issue/10002",
                      "fields": {
                        "summary": "Add feature X",
                        "description": null,
                        "status": {"name": "To Do"},
                        "priority": {"name": "Medium"},
                        "assignee": null,
                        "labels": [],
                        "created": "2024-01-03T12:00:00.000+0000",
                        "updated": "2024-01-04T12:00:00.000+0000"
                      }
                    }
                  ]
                }
                """;
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        PlatformTaskFilter filter = PlatformTaskFilter.builder().maxResults(10).build();
        List<PlatformTaskDto> tasks = adapter.fetchTasks("PROJ", filter);

        assertNotNull(tasks);
        assertEquals(2, tasks.size());

        PlatformTaskDto task1 = tasks.get(0);
        assertEquals("PROJ-1", task1.getExternalId());
        assertEquals("https://example.atlassian.net/browse/PROJ-1", task1.getExternalUrl());
        assertEquals("Fix the login bug", task1.getTitle());
        assertNotNull(task1.getDescription());
        assertEquals("In Progress", task1.getStatus());
        assertEquals("High", task1.getPriority());
        assertEquals("John Doe", task1.getAssignee());
        assertEquals(List.of("bug", "urgent"), task1.getLabels());
        assertNotNull(task1.getCreatedAt());
        assertNotNull(task1.getUpdatedAt());

        PlatformTaskDto task2 = tasks.get(1);
        assertEquals("PROJ-2", task2.getExternalId());
        assertEquals("Add feature X", task2.getTitle());
        assertNull(task2.getDescription());
        assertEquals("To Do", task2.getStatus());
        assertNull(task2.getAssignee());
        assertTrue(task2.getLabels().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchTasks_with_statusFilter() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = "{\"issues\": []}";
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .status("In Progress")
                .assignee("john")
                .maxResults(25)
                .build();
        List<PlatformTaskDto> tasks = adapter.fetchTasks("PROJ", filter);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchTasks_with_nullFilter() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = "{\"issues\": []}";
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        List<PlatformTaskDto> tasks = adapter.fetchTasks("PROJ", null);
        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_fetchTasksFails() {
        configureAdapter();
        setupGetMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("500 Server Error")));

        assertThrows(RuntimeException.class, () -> adapter.fetchTasks("PROJ", null));
    }

    // --- getTask ---

    @Test
    @SuppressWarnings("unchecked")
    void should_getTask_when_validExternalId() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = """
                {
                  "key": "PROJ-42",
                  "self": "https://example.atlassian.net/rest/api/3/issue/10042",
                  "fields": {
                    "summary": "Implement auth",
                    "description": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Auth desc"}]}]},
                    "status": {"name": "Done"},
                    "priority": {"name": "Critical"},
                    "assignee": {"displayName": "Jane Smith"},
                    "labels": ["feature"],
                    "created": "2024-02-01T10:30:00.000+0000",
                    "updated": "2024-02-05T14:00:00.000+0000"
                  }
                }
                """;
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        PlatformTaskDto task = adapter.getTask("PROJ-42");

        assertNotNull(task);
        assertEquals("PROJ-42", task.getExternalId());
        assertEquals("https://example.atlassian.net/browse/PROJ-42", task.getExternalUrl());
        assertEquals("Implement auth", task.getTitle());
        assertEquals("Done", task.getStatus());
        assertEquals("Critical", task.getPriority());
        assertEquals("Jane Smith", task.getAssignee());
        assertEquals(List.of("feature"), task.getLabels());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_getTask_when_fieldsHaveNulls() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = """
                {
                  "key": "PROJ-99",
                  "fields": {
                    "summary": "Minimal issue",
                    "description": null,
                    "status": null,
                    "priority": null,
                    "assignee": null,
                    "labels": null,
                    "created": null,
                    "updated": null
                  }
                }
                """;
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        PlatformTaskDto task = adapter.getTask("PROJ-99");

        assertNotNull(task);
        assertEquals("PROJ-99", task.getExternalId());
        assertEquals("Minimal issue", task.getTitle());
        assertNull(task.getDescription());
        assertNull(task.getStatus());
        assertNull(task.getPriority());
        assertNull(task.getAssignee());
        assertNull(task.getCreatedAt());
        assertNull(task.getUpdatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getTaskFails() {
        configureAdapter();
        setupGetMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("404 Not Found")));

        assertThrows(RuntimeException.class, () -> adapter.getTask("PROJ-999"));
    }

    // --- updateTaskStatus ---

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_when_transitionFound() {
        configureAdapter();

        String transitionsResponse = """
                {
                  "transitions": [
                    {"id": "11", "name": "To Do"},
                    {"id": "21", "name": "In Progress"},
                    {"id": "31", "name": "Done"}
                  ]
                }
                """;

        // Setup GET and POST mocks sharing same responseSpec
        setupGetMock();
        setupPostMock();
        // Sequential return: first call (GET transitions) returns JSON, second call (POST) returns empty
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(transitionsResponse))
                .thenReturn(Mono.just("{}"));

        adapter.updateTaskStatus("PROJ-1", "Done", null);

        verify(webClient).post();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatusAndAddComment_when_commentProvided() {
        configureAdapter();

        String transitionsResponse = """
                {
                  "transitions": [
                    {"id": "31", "name": "Done"}
                  ]
                }
                """;

        setupGetMock();
        setupPostMock();
        // Sequential: GET transitions -> POST transition -> POST comment
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(transitionsResponse))
                .thenReturn(Mono.just("{}"))
                .thenReturn(Mono.just("{}"));

        adapter.updateTaskStatus("PROJ-1", "Done", "Completed the work");

        // post() is called twice: once for transition, once for comment
        verify(webClient, atLeast(2)).post();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_noMatchingTransition() {
        configureAdapter();
        setupGetMock();
        String transitionsResponse = """
                {
                  "transitions": [
                    {"id": "11", "name": "To Do"}
                  ]
                }
                """;
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(transitionsResponse));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adapter.updateTaskStatus("PROJ-1", "NonExistent", null));
        assertTrue(ex.getMessage().contains("No transition found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_matchTransition_caseInsensitive() {
        configureAdapter();

        String transitionsResponse = """
                {
                  "transitions": [
                    {"id": "31", "name": "Done"}
                  ]
                }
                """;

        setupGetMock();
        setupPostMock();
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(transitionsResponse))
                .thenReturn(Mono.just("{}"));

        // "done" should match "Done"
        adapter.updateTaskStatus("PROJ-1", "done", null);

        verify(webClient).post();
    }

    // --- addComment ---

    @Test
    @SuppressWarnings("unchecked")
    void should_addComment_when_validInput() {
        configureAdapter();
        setupPostMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"id\":\"12345\"}"));

        adapter.addComment("PROJ-1", "This is a test comment");

        verify(webClient).post();
        verify(requestBodySpec).bodyValue(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_addCommentFails() {
        configureAdapter();
        setupPostMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("403 Forbidden")));

        assertThrows(RuntimeException.class, () -> adapter.addComment("PROJ-1", "comment"));
    }

    // --- getAvailableStatuses ---

    @Test
    @SuppressWarnings("unchecked")
    void should_getAvailableStatuses_when_validProjectKey() {
        configureAdapter();
        setupGetMock();

        String jsonResponse = """
                [
                  {
                    "name": "Bug",
                    "statuses": [
                      {"name": "To Do"},
                      {"name": "In Progress"},
                      {"name": "Done"}
                    ]
                  },
                  {
                    "name": "Story",
                    "statuses": [
                      {"name": "To Do"},
                      {"name": "In Progress"},
                      {"name": "In Review"},
                      {"name": "Done"}
                    ]
                  }
                ]
                """;
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        List<String> statuses = adapter.getAvailableStatuses("PROJ");

        assertNotNull(statuses);
        // Distinct: To Do, In Progress, Done, In Review
        assertEquals(4, statuses.size());
        assertTrue(statuses.contains("To Do"));
        assertTrue(statuses.contains("In Progress"));
        assertTrue(statuses.contains("Done"));
        assertTrue(statuses.contains("In Review"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmptyStatuses_when_noIssueTypes() {
        configureAdapter();
        setupGetMock();

        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("[]"));

        List<String> statuses = adapter.getAvailableStatuses("PROJ");

        assertNotNull(statuses);
        assertTrue(statuses.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getAvailableStatusesFails() {
        configureAdapter();
        setupGetMock();
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("404")));

        assertThrows(RuntimeException.class, () -> adapter.getAvailableStatuses("BADPROJ"));
    }
}
