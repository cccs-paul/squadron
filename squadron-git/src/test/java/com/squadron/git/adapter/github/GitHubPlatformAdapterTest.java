package com.squadron.git.adapter.github;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubPlatformAdapterTest {

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

    private GitHubPlatformAdapter adapter;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter = new GitHubPlatformAdapter(webClientBuilder);
    }

    @Test
    void should_returnGitHub_when_getPlatformType() {
        assertEquals("GITHUB", adapter.getPlatformType());
    }

    @Test
    void should_createPullRequest_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .repoOwner("owner")
                .repoName("repo")
                .title("Test PR")
                .description("A test pull request")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .accessToken("test-token")
                .build();

        Map<String, Object> apiResponse = Map.of(
                "number", 42,
                "html_url", "https://github.com/owner/repo/pull/42",
                "title", "Test PR"
        );

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);

        assertNotNull(result);
        assertEquals("GITHUB", result.getPlatform());
        assertEquals("42", result.getExternalPrId());
        assertEquals("https://github.com/owner/repo/pull/42", result.getExternalPrUrl());
        assertEquals("Test PR", result.getTitle());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
        assertEquals("OPEN", result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
    }

    @Test
    void should_throwPlatformIntegrationException_when_createPullRequestReturnsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("owner")
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
                .repoOwner("owner")
                .repoName("repo")
                .title("Test PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        422, "Unprocessable Entity", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.createPullRequest(request));
        assertEquals("GITHUB", ex.getPlatform());
    }

    @Test
    void should_createPullRequest_when_descriptionIsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("owner")
                .repoName("repo")
                .title("Test PR")
                .description(null)
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        Map<String, Object> apiResponse = Map.of(
                "number", 1,
                "html_url", "https://github.com/owner/repo/pull/1",
                "title", "Test PR"
        );

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);
        assertNotNull(result);
    }

    @Test
    void should_mergePullRequest_when_mergeStrategy() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("owner", "repo", "42", "MERGE", "token"));
    }

    @Test
    void should_mergePullRequest_when_squashStrategy() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("owner", "repo", "42", "SQUASH", "token"));
    }

    @Test
    void should_mergePullRequest_when_rebaseStrategy() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("owner", "repo", "42", "REBASE", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_mergePullRequestApiError() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        409, "Conflict", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.mergePullRequest("owner", "repo", "42", "MERGE", "token"));
        assertEquals("GITHUB", ex.getPlatform());
    }

    @Test
    void should_addReviewComment_successfully() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.addReviewComment("owner", "repo", "42", "LGTM", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_addReviewCommentApiError() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.addReviewComment("owner", "repo", "42", "LGTM", "token"));
        assertEquals("GITHUB", ex.getPlatform());
    }

    @Test
    void should_getDiff_when_filesExist() {
        List<Map<String, Object>> filesResponse = List.of(
                Map.of("filename", "src/Main.java", "status", "modified",
                        "additions", 10, "deletions", 3, "patch", "@@ -1,3 +1,10 @@"),
                Map.of("filename", "src/Test.java", "status", "added",
                        "additions", 20, "deletions", 0, "patch", "@@ -0,0 +1,20 @@")
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(filesResponse));

        DiffResult result = adapter.getDiff("owner", "repo", "42", "token");

        assertNotNull(result);
        assertEquals(2, result.getFiles().size());
        assertEquals(30, result.getTotalAdditions());
        assertEquals(3, result.getTotalDeletions());
        assertEquals("src/Main.java", result.getFiles().get(0).getFilename());
        assertEquals("modified", result.getFiles().get(0).getStatus());
        assertEquals(10, result.getFiles().get(0).getAdditions());
        assertEquals(3, result.getFiles().get(0).getDeletions());
    }

    @Test
    void should_returnEmptyDiff_when_responseIsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        DiffResult result = adapter.getDiff("owner", "repo", "42", "token");

        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(0, result.getTotalDeletions());
    }

    @Test
    void should_throwPlatformIntegrationException_when_getDiffApiError() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        403, "Forbidden", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.getDiff("owner", "repo", "42", "token"));
        assertEquals("GITHUB", ex.getPlatform());
    }

    @Test
    void should_requestReviewers_successfully() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.requestReviewers("owner", "repo", "42",
                List.of("reviewer1", "reviewer2"), "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_requestReviewersApiError() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        422, "Unprocessable Entity", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.requestReviewers("owner", "repo", "42",
                        List.of("reviewer1"), "token"));
        assertEquals("GITHUB", ex.getPlatform());
    }

    @Test
    void should_getPullRequest_when_open() {
        Map<String, Object> apiResponse = Map.of(
                "number", 42,
                "html_url", "https://github.com/owner/repo/pull/42",
                "title", "Test PR",
                "state", "open",
                "merged", false,
                "head", Map.of("ref", "feature/test"),
                "base", Map.of("ref", "main")
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "42", "token");

        assertNotNull(result);
        assertEquals("GITHUB", result.getPlatform());
        assertEquals("42", result.getExternalPrId());
        assertEquals("OPEN", result.getStatus());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
    }

    @Test
    void should_getPullRequest_when_merged() {
        Map<String, Object> apiResponse = Map.of(
                "number", 42,
                "html_url", "https://github.com/owner/repo/pull/42",
                "title", "Test PR",
                "state", "closed",
                "merged", true,
                "head", Map.of("ref", "feature/test"),
                "base", Map.of("ref", "main")
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "42", "token");

        assertEquals("MERGED", result.getStatus());
    }

    @Test
    void should_getPullRequest_when_closedNotMerged() {
        Map<String, Object> apiResponse = Map.of(
                "number", 42,
                "html_url", "https://github.com/owner/repo/pull/42",
                "title", "Test PR",
                "state", "closed",
                "merged", false,
                "head", Map.of("ref", "feature/test"),
                "base", Map.of("ref", "main")
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "42", "token");

        assertEquals("CLOSED", result.getStatus());
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestReturnsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("owner", "repo", "42", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestApiError() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("owner", "repo", "42", "token"));
        assertEquals("GITHUB", ex.getPlatform());
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
