package com.squadron.git.adapter.bitbucket;

import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.PullRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BitbucketPlatformAdapterTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private BitbucketPlatformAdapter adapter;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter = new BitbucketPlatformAdapter(webClientBuilder);
    }

    @Test
    void should_returnBitbucket_when_getPlatformType() {
        assertEquals("BITBUCKET", adapter.getPlatformType());
    }

    @Test
    void should_createPullRequest_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .repoOwner("workspace")
                .repoName("repo-slug")
                .title("Test PR")
                .description("A test pull request")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .accessToken("test-token")
                .build();

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 99);
        apiResponse.put("title", "Test PR");
        apiResponse.put("links", Map.of(
                "html", Map.of("href", "https://bitbucket.org/workspace/repo-slug/pull-requests/99")
        ));

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);

        assertNotNull(result);
        assertEquals("BITBUCKET", result.getPlatform());
        assertEquals("99", result.getExternalPrId());
        assertEquals("https://bitbucket.org/workspace/repo-slug/pull-requests/99", result.getExternalPrUrl());
        assertEquals("Test PR", result.getTitle());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
        assertEquals("OPEN", result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
    }

    @Test
    void should_createPullRequest_when_withReviewers() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("workspace")
                .repoName("repo")
                .title("PR with reviewers")
                .sourceBranch("feature")
                .targetBranch("main")
                .reviewers(List.of("user1", "user2"))
                .accessToken("token")
                .build();

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 1);
        apiResponse.put("title", "PR with reviewers");
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/1")));

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);
        assertNotNull(result);
    }

    @Test
    void should_createPullRequest_when_descriptionIsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("workspace")
                .repoName("repo")
                .title("Test PR")
                .description(null)
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 1);
        apiResponse.put("title", "Test PR");
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/1")));

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);
        assertNotNull(result);
    }

    @Test
    void should_throwPlatformIntegrationException_when_createPullRequestReturnsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("workspace")
                .repoName("repo")
                .title("Test PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        assertThrows(PlatformIntegrationException.class, () -> adapter.createPullRequest(request));
    }

    @Test
    void should_throwPlatformIntegrationException_when_createPullRequestApiError() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("workspace")
                .repoName("repo")
                .title("Test PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        400, "Bad Request", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.createPullRequest(request));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_mergePullRequest_when_defaultStrategy() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("workspace", "repo", "99", "MERGE", "token"));
    }

    @Test
    void should_mergePullRequest_when_squashStrategy() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("workspace", "repo", "99", "SQUASH", "token"));
    }

    @Test
    void should_mergePullRequest_when_rebaseStrategy() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("workspace", "repo", "99", "REBASE", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_mergePullRequestApiError() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        409, "Conflict", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.mergePullRequest("workspace", "repo", "99", "MERGE", "token"));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_addReviewComment_successfully() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.addReviewComment("workspace", "repo", "99", "Looks good!", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_addReviewCommentApiError() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.addReviewComment("workspace", "repo", "99", "Comment", "token"));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_getDiff_when_valuesExist() {
        Map<String, Object> value1 = new HashMap<>();
        value1.put("status", "modified");
        value1.put("lines_added", 5);
        value1.put("lines_removed", 2);
        value1.put("new", Map.of("path", "src/Main.java"));

        Map<String, Object> value2 = new HashMap<>();
        value2.put("status", "added");
        value2.put("lines_added", 15);
        value2.put("lines_removed", 0);
        value2.put("new", Map.of("path", "src/New.java"));

        Map<String, Object> apiResponse = Map.of("values", List.of(value1, value2));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertNotNull(result);
        assertEquals(2, result.getFiles().size());
        assertEquals(20, result.getTotalAdditions());
        assertEquals(2, result.getTotalDeletions());
        assertEquals("src/Main.java", result.getFiles().get(0).getFilename());
        assertEquals("modified", result.getFiles().get(0).getStatus());
        assertEquals("src/New.java", result.getFiles().get(1).getFilename());
        assertEquals("added", result.getFiles().get(1).getStatus());
    }

    @Test
    void should_getDiff_when_removedStatus() {
        Map<String, Object> value = new HashMap<>();
        value.put("status", "removed");
        value.put("lines_added", 0);
        value.put("lines_removed", 10);
        value.put("new", Map.of("path", "src/Deleted.java"));

        Map<String, Object> apiResponse = Map.of("values", List.of(value));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertEquals(1, result.getFiles().size());
        assertEquals("deleted", result.getFiles().get(0).getStatus());
    }

    @Test
    void should_getDiff_when_unknownStatus() {
        Map<String, Object> value = new HashMap<>();
        value.put("status", "renamed");
        value.put("lines_added", 0);
        value.put("lines_removed", 0);
        value.put("new", Map.of("path", "src/Renamed.java"));

        Map<String, Object> apiResponse = Map.of("values", List.of(value));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertEquals("modified", result.getFiles().get(0).getStatus());
    }

    @Test
    void should_returnEmptyDiff_when_responseIsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(0, result.getTotalDeletions());
    }

    @Test
    void should_returnEmptyDiff_when_valuesKeyIsNull() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("values", null);

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
    }

    @Test
    void should_throwPlatformIntegrationException_when_getDiffApiError() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        403, "Forbidden", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.getDiff("workspace", "repo", "99", "token"));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_requestReviewers_successfully() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.requestReviewers("workspace", "repo", "99",
                List.of("reviewer1", "reviewer2"), "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_requestReviewersApiError() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        422, "Unprocessable Entity", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.requestReviewers("workspace", "repo", "99",
                        List.of("reviewer1"), "token"));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_getPullRequest_when_openState() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 99);
        apiResponse.put("title", "Test PR");
        apiResponse.put("state", "OPEN");
        apiResponse.put("source", Map.of("branch", Map.of("name", "feature/test")));
        apiResponse.put("destination", Map.of("branch", Map.of("name", "main")));
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/99")));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("workspace", "repo", "99", "token");

        assertNotNull(result);
        assertEquals("BITBUCKET", result.getPlatform());
        assertEquals("99", result.getExternalPrId());
        assertEquals("OPEN", result.getStatus());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
        assertEquals("https://bitbucket.org/ws/repo/pull-requests/99", result.getExternalPrUrl());
    }

    @Test
    void should_getPullRequest_when_mergedState() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 99);
        apiResponse.put("title", "Test PR");
        apiResponse.put("state", "MERGED");
        apiResponse.put("source", Map.of("branch", Map.of("name", "feature/test")));
        apiResponse.put("destination", Map.of("branch", Map.of("name", "main")));
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/99")));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("workspace", "repo", "99", "token");

        assertEquals("MERGED", result.getStatus());
    }

    @Test
    void should_getPullRequest_when_declinedState() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 99);
        apiResponse.put("title", "Test PR");
        apiResponse.put("state", "DECLINED");
        apiResponse.put("source", Map.of("branch", Map.of("name", "feature/test")));
        apiResponse.put("destination", Map.of("branch", Map.of("name", "main")));
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/99")));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("workspace", "repo", "99", "token");

        assertEquals("CLOSED", result.getStatus());
    }

    @Test
    void should_getPullRequest_when_supersededState() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", 99);
        apiResponse.put("title", "Test PR");
        apiResponse.put("state", "SUPERSEDED");
        apiResponse.put("source", Map.of("branch", Map.of("name", "feature/test")));
        apiResponse.put("destination", Map.of("branch", Map.of("name", "main")));
        apiResponse.put("links", Map.of("html", Map.of("href", "https://bitbucket.org/ws/repo/pull-requests/99")));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("workspace", "repo", "99", "token");

        assertEquals("CLOSED", result.getStatus());
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestReturnsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("workspace", "repo", "99", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestApiError() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("workspace", "repo", "99", "token"));
        assertEquals("BITBUCKET", ex.getPlatform());
    }

    @Test
    void should_getDiff_when_newFileFieldIsNull() {
        Map<String, Object> value = new HashMap<>();
        value.put("status", "modified");
        value.put("lines_added", 1);
        value.put("lines_removed", 1);
        value.put("new", null);

        Map<String, Object> apiResponse = Map.of("values", List.of(value));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("workspace", "repo", "99", "token");

        assertEquals("unknown", result.getFiles().get(0).getFilename());
    }

    // ---- WebClient mock chain helpers ----

    private void setupPostChain() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private void setupPutChain() {
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private void setupGetChain() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }
}
