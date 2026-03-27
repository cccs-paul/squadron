package com.squadron.platform.adapter.github;

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
class GitHubIssuesAdapterTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    private GitHubIssuesAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new GitHubIssuesAdapter(webClientBuilder);
    }

    private void configureAdapter() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        adapter.configure("https://api.github.com", "ghp_token");
    }

    private void mockGet(String responseJson) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseJson));
    }

    private void mockPatch(String responseJson) {
        when(webClient.patch()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseJson));
    }

    private void mockPost(String responseJson) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseJson));
    }

    // --- Existing tests ---

    @Test
    void should_returnCorrectPlatformType() {
        assertEquals("GITHUB", adapter.getPlatformType());
    }

    @Test
    void should_configureAdapter_withDefaultBaseUrl() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure(null, "ghp_token");

        verify(webClientBuilder).baseUrl("https://api.github.com");
    }

    @Test
    void should_configureAdapter_withCustomBaseUrl() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://github.mycompany.com/api/v3", "ghp_token");

        verify(webClientBuilder).baseUrl("https://github.mycompany.com/api/v3");
    }

    @Test
    void should_configureAdapter_withBlankBaseUrlUsesDefault() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("  ", "ghp_token");

        verify(webClientBuilder).baseUrl("https://api.github.com");
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
        mockGet("{\"login\":\"user\"}");

        boolean result = adapter.testConnection();
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnFalse_when_testConnectionFails() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter.configure("https://api.github.com", "bad-token");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("401")));

        boolean result = adapter.testConnection();
        assertFalse(result);
    }

    @Test
    void should_returnAvailableStatuses() {
        List<String> statuses = adapter.getAvailableStatuses("owner/repo");
        assertEquals(List.of("open", "closed"), statuses);
    }

    // --- fetchTasks tests ---

    @Test
    void should_fetchTasks_returnsIssues() {
        configureAdapter();
        String json = """
                [
                  {
                    "number": 1,
                    "html_url": "https://github.com/octocat/hello/issues/1",
                    "title": "Bug in login",
                    "body": "Login fails on Chrome",
                    "state": "open",
                    "assignee": {"login": "user1"},
                    "labels": [{"name": "bug"}, {"name": "urgent"}],
                    "created_at": "2024-01-01T00:00:00Z",
                    "updated_at": "2024-01-02T00:00:00Z"
                  },
                  {
                    "number": 2,
                    "html_url": "https://github.com/octocat/hello/issues/2",
                    "title": "Add dark mode",
                    "body": null,
                    "state": "closed",
                    "assignee": null,
                    "labels": [],
                    "created_at": "2024-02-01T00:00:00Z",
                    "updated_at": "2024-02-05T00:00:00Z"
                  }
                ]
                """;
        mockGet(json);

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .status("open")
                .maxResults(30)
                .build();
        List<PlatformTaskDto> tasks = adapter.fetchTasks("octocat/hello", filter);

        assertEquals(2, tasks.size());

        PlatformTaskDto first = tasks.get(0);
        assertEquals("octocat/hello#1", first.getExternalId());
        assertEquals("https://github.com/octocat/hello/issues/1", first.getExternalUrl());
        assertEquals("Bug in login", first.getTitle());
        assertEquals("Login fails on Chrome", first.getDescription());
        assertEquals("open", first.getStatus());
        assertNull(first.getPriority());
        assertEquals("user1", first.getAssignee());
        assertEquals(List.of("bug", "urgent"), first.getLabels());
        assertNotNull(first.getCreatedAt());
        assertNotNull(first.getUpdatedAt());

        PlatformTaskDto second = tasks.get(1);
        assertEquals("octocat/hello#2", second.getExternalId());
        assertEquals("closed", second.getStatus());
        assertNull(second.getAssignee());
        assertNull(second.getDescription());
        assertEquals(List.of(), second.getLabels());
    }

    @Test
    void should_fetchTasks_filterOutPullRequests() {
        configureAdapter();
        String json = """
                [
                  {
                    "number": 1,
                    "html_url": "https://github.com/octocat/hello/issues/1",
                    "title": "Real issue",
                    "body": "A real issue",
                    "state": "open",
                    "assignee": null,
                    "labels": [],
                    "created_at": "2024-01-01T00:00:00Z",
                    "updated_at": "2024-01-02T00:00:00Z"
                  },
                  {
                    "number": 10,
                    "html_url": "https://github.com/octocat/hello/pull/10",
                    "title": "A pull request",
                    "body": "PR body",
                    "state": "open",
                    "assignee": null,
                    "labels": [],
                    "pull_request": {
                      "url": "https://api.github.com/repos/octocat/hello/pulls/10"
                    },
                    "created_at": "2024-03-01T00:00:00Z",
                    "updated_at": "2024-03-02T00:00:00Z"
                  }
                ]
                """;
        mockGet(json);

        List<PlatformTaskDto> tasks = adapter.fetchTasks("octocat/hello", null);

        assertEquals(1, tasks.size());
        assertEquals("Real issue", tasks.get(0).getTitle());
        assertEquals("octocat/hello#1", tasks.get(0).getExternalId());
    }

    @Test
    void should_fetchTasks_withEmptyResult() {
        configureAdapter();
        mockGet("[]");

        List<PlatformTaskDto> tasks = adapter.fetchTasks("octocat/hello", null);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void should_fetchTasks_withAssigneeFilter() {
        configureAdapter();
        String json = """
                [
                  {
                    "number": 5,
                    "html_url": "https://github.com/octocat/hello/issues/5",
                    "title": "Assigned issue",
                    "body": "Assigned to jdoe",
                    "state": "open",
                    "assignee": {"login": "jdoe"},
                    "labels": [],
                    "created_at": "2024-01-01T00:00:00Z",
                    "updated_at": "2024-01-02T00:00:00Z"
                  }
                ]
                """;
        mockGet(json);

        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .assignee("jdoe")
                .build();
        List<PlatformTaskDto> tasks = adapter.fetchTasks("octocat/hello", filter);

        assertEquals(1, tasks.size());
        assertEquals("jdoe", tasks.get(0).getAssignee());

        verify(requestHeadersUriSpec).uri(contains("assignee=jdoe"));
    }

    // --- getTask tests ---

    @Test
    void should_getTask_returnsSingleIssue() {
        configureAdapter();
        String json = """
                {
                  "number": 42,
                  "html_url": "https://github.com/octocat/hello/issues/42",
                  "title": "Important bug",
                  "body": "This is critical",
                  "state": "open",
                  "assignee": {"login": "alice"},
                  "labels": [{"name": "critical"}],
                  "created_at": "2024-06-01T12:00:00Z",
                  "updated_at": "2024-06-02T15:30:00Z"
                }
                """;
        mockGet(json);

        PlatformTaskDto task = adapter.getTask("octocat/hello#42");

        assertEquals("octocat/hello#42", task.getExternalId());
        assertEquals("https://github.com/octocat/hello/issues/42", task.getExternalUrl());
        assertEquals("Important bug", task.getTitle());
        assertEquals("This is critical", task.getDescription());
        assertEquals("open", task.getStatus());
        assertEquals("alice", task.getAssignee());
        assertEquals(List.of("critical"), task.getLabels());
        assertNull(task.getPriority());

        verify(requestHeadersUriSpec).uri("/repos/octocat/hello/issues/42");
    }

    @Test
    void should_getTask_throwsOnApiError() {
        configureAdapter();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("404 Not Found")));

        assertThrows(RuntimeException.class, () -> adapter.getTask("octocat/hello#999"));
    }

    // --- updateTaskStatus tests ---

    @Test
    void should_updateTaskStatus_callsPatch() {
        configureAdapter();
        String patchResponse = """
                {
                  "number": 1,
                  "state": "closed"
                }
                """;
        mockPatch(patchResponse);

        adapter.updateTaskStatus("octocat/hello#1", "closed", null);

        verify(webClient).patch();
        verify(requestBodyUriSpec).uri("/repos/octocat/hello/issues/1");
    }

    @Test
    void should_updateTaskStatus_withCommentCallsPatchAndPost() {
        configureAdapter();
        String patchResponse = """
                {
                  "number": 1,
                  "state": "closed"
                }
                """;
        mockPatch(patchResponse);

        // After patch, addComment will use post — need to reset and set up post mock
        // Since mockPatch already uses requestHeadersSpec, we need a fresh setup for post
        // We'll verify patch is called, and then the adapter will call addComment internally
        // For the post call within addComment:
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        // requestBodyUriSpec.uri and requestBodySpec.bodyValue already stubbed from mockPatch

        String commentResponse = """
                {
                  "id": 100,
                  "body": "Closing this issue"
                }
                """;
        // The post will reuse the same mock chain, returning the patchResponse for bodyToMono
        // That's fine — we just care that the methods are called

        adapter.updateTaskStatus("octocat/hello#1", "closed", "Closing this issue");

        verify(webClient).patch();
        verify(webClient).post();
    }

    @Test
    void should_updateTaskStatus_withBlankCommentSkipsPost() {
        configureAdapter();
        String patchResponse = """
                {
                  "number": 1,
                  "state": "open"
                }
                """;
        mockPatch(patchResponse);

        adapter.updateTaskStatus("octocat/hello#1", "open", "   ");

        verify(webClient).patch();
        verify(webClient, never()).post();
    }

    // --- addComment tests ---

    @Test
    void should_addComment_callsPost() {
        configureAdapter();
        String commentResponse = """
                {
                  "id": 200,
                  "body": "This is a comment"
                }
                """;
        mockPost(commentResponse);

        adapter.addComment("octocat/hello#7", "This is a comment");

        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/repos/octocat/hello/issues/7/comments");
    }

    // --- parseExternalId tests ---

    @Test
    void should_parseExternalId_correctly() {
        String[] parts = adapter.parseExternalId("octocat/hello#42");
        assertEquals("octocat", parts[0]);
        assertEquals("hello", parts[1]);
        assertEquals("42", parts[2]);
    }

    @Test
    void should_parseExternalId_throwsOnInvalidFormat_noHash() {
        assertThrows(IllegalArgumentException.class, () -> adapter.parseExternalId("octocat/hello"));
    }

    @Test
    void should_parseExternalId_throwsOnInvalidFormat_noSlash() {
        assertThrows(IllegalArgumentException.class, () -> adapter.parseExternalId("octocathello#42"));
    }
}
