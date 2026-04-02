package com.squadron.platform.adapter.azuredevops;

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
class AzureDevOpsAdapterTest {

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

    private AzureDevOpsAdapter adapter;

    private static final String BASE_URL = "https://dev.azure.com";

    @BeforeEach
    void setUp() {
        adapter = new AzureDevOpsAdapter(sslHelper);
    }

    private void configureAdapter() {
        when(sslHelper.trustedBuilder()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        adapter.configure(BASE_URL, Map.of("pat", "base64-encoded-pat"));
    }

    // --- Existing tests (preserved) ---

    @Test
    void should_returnCorrectPlatformType() {
        assertEquals("AZURE_DEVOPS", adapter.getPlatformType());
    }

    @Test
    void should_configureAdapter() {
        when(sslHelper.trustedBuilder()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://dev.azure.com/myorg", Map.of("pat", "base64-encoded-pat"));

        verify(webClientBuilder).baseUrl("https://dev.azure.com/myorg");
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

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"count\":1,\"value\":[]}"));

        boolean result = adapter.testConnection();
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnFalse_when_testConnectionFails() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("401")));

        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    // --- fetchTasks tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchTasks_when_validProjectKey() {
        configureAdapter();

        String wiqlResponse = """
                {"workItems": [{"id": 101}, {"id": 102}]}
                """;

        String detailsResponse = """
                {"value": [
                    {
                        "id": 101,
                        "fields": {
                            "System.Title": "Fix login bug",
                            "System.Description": "<p>Login fails on mobile</p>",
                            "System.State": "Active",
                            "Microsoft.VSTS.Common.Priority": 2,
                            "System.AssignedTo": {"displayName": "John Doe", "uniqueName": "john@example.com"},
                            "System.Tags": "bug; frontend",
                            "System.CreatedDate": "2025-01-15T10:30:00Z",
                            "System.ChangedDate": "2025-01-16T14:00:00Z"
                        }
                    },
                    {
                        "id": 102,
                        "fields": {
                            "System.Title": "Add dark mode",
                            "System.Description": null,
                            "System.State": "New",
                            "Microsoft.VSTS.Common.Priority": 3,
                            "System.AssignedTo": "Jane Smith",
                            "System.Tags": "enhancement",
                            "System.CreatedDate": "2025-01-17T08:00:00Z",
                            "System.ChangedDate": "2025-01-17T08:00:00Z"
                        }
                    }
                ]}
                """;

        // Mock POST for WIQL query
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(wiqlResponse));

        // Mock GET for batch details — need a fresh mock chain
        WebClient.RequestHeadersUriSpec getUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec getHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec getResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(detailsResponse));

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .maxResults(50)
                .build();

        List<PlatformTaskDto> tasks = adapter.fetchTasks("myorg/myproject", filter);

        assertEquals(2, tasks.size());

        PlatformTaskDto first = tasks.get(0);
        assertEquals("101", first.getExternalId());
        assertEquals("Fix login bug", first.getTitle());
        assertEquals("<p>Login fails on mobile</p>", first.getDescription());
        assertEquals("Active", first.getStatus());
        assertEquals("High", first.getPriority());
        assertEquals("John Doe", first.getAssignee());
        assertEquals(List.of("bug", "frontend"), first.getLabels());
        assertEquals("https://dev.azure.com/myorg/myproject/_workitems/edit/101", first.getExternalUrl());
        assertNotNull(first.getCreatedAt());
        assertNotNull(first.getUpdatedAt());

        PlatformTaskDto second = tasks.get(1);
        assertEquals("102", second.getExternalId());
        assertEquals("Add dark mode", second.getTitle());
        assertEquals("New", second.getStatus());
        assertEquals("Medium", second.getPriority());
        assertEquals("Jane Smith", second.getAssignee());
        assertEquals(List.of("enhancement"), second.getLabels());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fetchTasks_withFilterConditions() {
        configureAdapter();

        String wiqlResponse = """
                {"workItems": [{"id": 200}]}
                """;

        String detailsResponse = """
                {"value": [
                    {
                        "id": 200,
                        "fields": {
                            "System.Title": "Filtered task",
                            "System.State": "Active",
                            "Microsoft.VSTS.Common.Priority": 1,
                            "System.CreatedDate": "2025-02-01T12:00:00Z",
                            "System.ChangedDate": "2025-02-01T12:00:00Z"
                        }
                    }
                ]}
                """;

        // Mock POST for WIQL
        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(uriCaptor.capture())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(wiqlResponse));

        // Mock GET for batch
        WebClient.RequestHeadersUriSpec getUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec getHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec getResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(detailsResponse));

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .status("Active")
                .assignee("john@example.com")
                .maxResults(10)
                .build();

        List<PlatformTaskDto> tasks = adapter.fetchTasks("myorg/myproject", filter);

        assertEquals(1, tasks.size());
        assertEquals("Critical", tasks.get(0).getPriority());

        // Verify the WIQL URI includes $top
        String capturedUri = uriCaptor.getValue();
        assertTrue(capturedUri.contains("$top=10"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmptyList_when_wiqlReturnsNoWorkItems() {
        configureAdapter();

        String wiqlResponse = """
                {"workItems": []}
                """;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(wiqlResponse));

        List<PlatformTaskDto> tasks = adapter.fetchTasks("myorg/myproject", null);

        assertTrue(tasks.isEmpty());
    }

    // --- getTask tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_getTask_when_validExternalId() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 42,
                    "fields": {
                        "System.Title": "Implement caching",
                        "System.Description": "<div>Add Redis caching layer</div>",
                        "System.State": "Resolved",
                        "Microsoft.VSTS.Common.Priority": 2,
                        "System.AssignedTo": {"displayName": "Alice Wonder", "uniqueName": "alice@example.com"},
                        "System.Tags": "backend; performance; cache",
                        "System.CreatedDate": "2025-03-01T09:00:00Z",
                        "System.ChangedDate": "2025-03-10T17:30:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("42");

        assertEquals("42", task.getExternalId());
        assertEquals("Implement caching", task.getTitle());
        assertEquals("<div>Add Redis caching layer</div>", task.getDescription());
        assertEquals("Resolved", task.getStatus());
        assertEquals("High", task.getPriority());
        assertEquals("Alice Wonder", task.getAssignee());
        assertEquals(List.of("backend", "performance", "cache"), task.getLabels());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        assertTrue(task.getExternalUrl().contains("/_workitems/edit/42"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_getTask_withNullFields() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 99,
                    "fields": {
                        "System.Title": "Minimal task",
                        "System.State": "New",
                        "System.CreatedDate": "2025-04-01T12:00:00Z",
                        "System.ChangedDate": "2025-04-01T12:00:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("99");

        assertEquals("99", task.getExternalId());
        assertEquals("Minimal task", task.getTitle());
        assertNull(task.getDescription());
        assertEquals("New", task.getStatus());
        assertNull(task.getPriority());
        assertNull(task.getAssignee());
        assertTrue(task.getLabels().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_mapPriority1_toCritical() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 1,
                    "fields": {
                        "System.Title": "Critical bug",
                        "System.State": "Active",
                        "Microsoft.VSTS.Common.Priority": 1,
                        "System.CreatedDate": "2025-01-01T00:00:00Z",
                        "System.ChangedDate": "2025-01-01T00:00:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("1");
        assertEquals("Critical", task.getPriority());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_mapPriority4_toLow() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 2,
                    "fields": {
                        "System.Title": "Low priority task",
                        "System.State": "New",
                        "Microsoft.VSTS.Common.Priority": 4,
                        "System.CreatedDate": "2025-01-01T00:00:00Z",
                        "System.ChangedDate": "2025-01-01T00:00:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("2");
        assertEquals("Low", task.getPriority());
    }

    // --- updateTaskStatus tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_withPatchRequest() {
        configureAdapter();

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);

        when(webClient.patch()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(uriCaptor.capture())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(bodyCaptor.capture())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

        adapter.updateTaskStatus("123", "Closed", null);

        String capturedUri = uriCaptor.getValue();
        assertTrue(capturedUri.contains("/123"));
        assertTrue(capturedUri.contains("api-version=7.0"));

        String capturedBody = (String) bodyCaptor.getValue();
        assertTrue(capturedBody.contains("System.State"));
        assertTrue(capturedBody.contains("Closed"));
        assertTrue(capturedBody.contains("replace"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_updateTaskStatus_andAddComment() {
        configureAdapter();

        // Mock PATCH for status update
        when(webClient.patch()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

        // Mock POST for comment
        WebClient.RequestBodyUriSpec postUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec postBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec postHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec postResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(postUriSpec);
        when(postUriSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

        adapter.updateTaskStatus("456", "Resolved", "Fixed the issue");

        verify(webClient).patch();
        verify(webClient).post();
    }

    // --- addComment tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_addComment_withPostRequest() {
        configureAdapter();

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(uriCaptor.capture())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));

        adapter.addComment("789", "This is a comment");

        String capturedUri = uriCaptor.getValue();
        assertTrue(capturedUri.contains("/789/comments"));
        assertTrue(capturedUri.contains("api-version=7.0-preview.4"));
    }

    // --- getAvailableStatuses tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_getAvailableStatuses_forProject() {
        configureAdapter();

        String typesResponse = """
                {"value": [
                    {"name": "Bug"},
                    {"name": "Task"},
                    {"name": "User Story"}
                ]}
                """;

        String statesResponse = """
                {"value": [
                    {"name": "New"},
                    {"name": "Active"},
                    {"name": "Resolved"},
                    {"name": "Closed"}
                ]}
                """;

        // Both calls are GET, so we need to chain them
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(typesResponse))
                .thenReturn(Mono.just(statesResponse));

        List<String> statuses = adapter.getAvailableStatuses("myorg/myproject");

        assertEquals(4, statuses.size());
        assertTrue(statuses.contains("New"));
        assertTrue(statuses.contains("Active"));
        assertTrue(statuses.contains("Resolved"));
        assertTrue(statuses.contains("Closed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_getAvailableStatuses_fallbackToFirstType_when_noTaskType() {
        configureAdapter();

        String typesResponse = """
                {"value": [
                    {"name": "Bug"},
                    {"name": "Feature"}
                ]}
                """;

        String statesResponse = """
                {"value": [
                    {"name": "New"},
                    {"name": "Committed"},
                    {"name": "Done"}
                ]}
                """;

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(uriCaptor.capture())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(typesResponse))
                .thenReturn(Mono.just(statesResponse));

        List<String> statuses = adapter.getAvailableStatuses("myorg/myproject");

        assertEquals(3, statuses.size());

        // Verify it used "Bug" (first type) instead of "Task"
        List<String> capturedUris = uriCaptor.getAllValues();
        String statesUri = capturedUris.get(1);
        assertTrue(statesUri.contains("/Bug/states"));
    }

    // --- Error handling tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_throwRuntimeException_when_fetchTasksFails() {
        configureAdapter();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("403 Forbidden")));

        assertThrows(RuntimeException.class,
                () -> adapter.fetchTasks("myorg/myproject", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwRuntimeException_when_getTaskFails() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("404 Not Found")));

        assertThrows(RuntimeException.class,
                () -> adapter.getTask("99999"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwRuntimeException_when_updateTaskStatusFails() {
        configureAdapter();

        when(webClient.patch()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("500 Internal Server Error")));

        assertThrows(RuntimeException.class,
                () -> adapter.updateTaskStatus("123", "Closed", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwRuntimeException_when_addCommentFails() {
        configureAdapter();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.error(new RuntimeException("401 Unauthorized")));

        assertThrows(RuntimeException.class,
                () -> adapter.addComment("123", "comment"));
    }

    // --- parseProjectKey tests ---

    @Test
    void should_parseProjectKey_correctly() {
        String[] parts = adapter.parseProjectKey("myorg/myproject");
        assertEquals("myorg", parts[0]);
        assertEquals("myproject", parts[1]);
    }

    @Test
    void should_throwException_when_projectKeyHasNoSlash() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.parseProjectKey("invalidkey"));
    }

    @Test
    void should_throwException_when_projectKeyIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.parseProjectKey(null));
    }

    @Test
    void should_throwException_when_projectKeyHasEmptyParts() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.parseProjectKey("/myproject"));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.parseProjectKey("myorg/"));
    }

    // --- getProjects tests ---

    @Test
    @SuppressWarnings("unchecked")
    void should_getProjects_when_configured() {
        configureAdapter();

        String jsonResponse = """
                {
                  "count": 2,
                  "value": [
                    {
                      "name": "MyProject",
                      "description": "An Azure DevOps project"
                    },
                    {
                      "name": "AnotherProject",
                      "description": null
                    }
                  ]
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        var projects = adapter.getProjects();

        assertNotNull(projects);
        assertEquals(2, projects.size());

        assertEquals("MyProject", projects.get(0).getKey());
        assertEquals("MyProject", projects.get(0).getName());
        assertEquals("An Azure DevOps project", projects.get(0).getDescription());
        assertTrue(projects.get(0).getUrl().contains("MyProject"));
        assertNull(projects.get(0).getAvatarUrl());

        assertEquals("AnotherProject", projects.get(1).getKey());
        assertNull(projects.get(1).getDescription());
        assertNull(projects.get(1).getAvatarUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmptyProjects_when_noProjects() {
        configureAdapter();

        String jsonResponse = """
                {
                  "count": 0,
                  "value": []
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

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
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("403")));

        assertThrows(RuntimeException.class, () -> adapter.getProjects());
    }

    // --- Tag parsing edge cases ---

    @Test
    @SuppressWarnings("unchecked")
    void should_handleEmptyTags() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 50,
                    "fields": {
                        "System.Title": "No tags task",
                        "System.State": "New",
                        "System.Tags": "",
                        "System.CreatedDate": "2025-01-01T00:00:00Z",
                        "System.ChangedDate": "2025-01-01T00:00:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("50");
        assertTrue(task.getLabels().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_handleAssignedToAsString() {
        configureAdapter();

        String workItemResponse = """
                {
                    "id": 60,
                    "fields": {
                        "System.Title": "String assignee task",
                        "System.State": "Active",
                        "System.AssignedTo": "plain.user@example.com",
                        "System.CreatedDate": "2025-01-01T00:00:00Z",
                        "System.ChangedDate": "2025-01-01T00:00:00Z"
                    }
                }
                """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(workItemResponse));

        PlatformTaskDto task = adapter.getTask("60");
        assertEquals("plain.user@example.com", task.getAssignee());
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
