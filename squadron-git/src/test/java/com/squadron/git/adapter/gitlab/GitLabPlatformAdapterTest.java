package com.squadron.git.adapter.gitlab;

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
class GitLabPlatformAdapterTest {

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

    private GitLabPlatformAdapter adapter;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        adapter = new GitLabPlatformAdapter(webClientBuilder);
    }

    @Test
    void should_returnGitLab_when_getPlatformType() {
        assertEquals("GITLAB", adapter.getPlatformType());
    }

    @Test
    void should_createMergeRequest_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .repoOwner("my-group")
                .repoName("my-project")
                .title("Test MR")
                .description("A test merge request")
                .sourceBranch("feature/test")
                .targetBranch("main")
                .accessToken("glpat-test-token")
                .build();

        Map<String, Object> apiResponse = Map.of(
                "iid", 10,
                "web_url", "https://gitlab.com/my-group/my-project/-/merge_requests/10",
                "title", "Test MR"
        );

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);

        assertNotNull(result);
        assertEquals("GITLAB", result.getPlatform());
        assertEquals("10", result.getExternalPrId());
        assertEquals("https://gitlab.com/my-group/my-project/-/merge_requests/10", result.getExternalPrUrl());
        assertEquals("Test MR", result.getTitle());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
        assertEquals("OPEN", result.getStatus());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(taskId, result.getTaskId());
    }

    @Test
    void should_createMergeRequest_when_descriptionIsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("owner")
                .repoName("repo")
                .title("Test MR")
                .description(null)
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        Map<String, Object> apiResponse = Map.of(
                "iid", 1,
                "web_url", "https://gitlab.com/owner/repo/-/merge_requests/1",
                "title", "Test MR"
        );

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.createPullRequest(request);
        assertNotNull(result);
    }

    @Test
    void should_throwPlatformIntegrationException_when_createMergeRequestReturnsNull() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("owner")
                .repoName("repo")
                .title("Test MR")
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
    void should_throwPlatformIntegrationException_when_createMergeRequestApiError() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .repoOwner("owner")
                .repoName("repo")
                .title("Test MR")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        setupPostChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        409, "Conflict", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.createPullRequest(request));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_mergeMergeRequest_when_defaultStrategy() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("owner", "repo", "10", "MERGE", "token"));
    }

    @Test
    void should_mergeMergeRequest_when_squashStrategy() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.mergePullRequest("owner", "repo", "10", "SQUASH", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_mergeMergeRequestApiError() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        405, "Method Not Allowed", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.mergePullRequest("owner", "repo", "10", "MERGE", "token"));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_addReviewComment_successfully() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.addReviewComment("owner", "repo", "10", "Looks good!", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_addReviewCommentApiError() {
        setupPostChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.addReviewComment("owner", "repo", "10", "Comment", "token"));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_getDiff_when_changesExist() {
        Map<String, Object> change1 = new HashMap<>();
        change1.put("new_path", "src/Main.java");
        change1.put("new_file", false);
        change1.put("deleted_file", false);
        change1.put("diff", "+added line\n-removed line\n context line");

        Map<String, Object> change2 = new HashMap<>();
        change2.put("new_path", "src/New.java");
        change2.put("new_file", true);
        change2.put("deleted_file", false);
        change2.put("diff", "+new line 1\n+new line 2");

        Map<String, Object> apiResponse = Map.of(
                "changes", List.of(change1, change2)
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("owner", "repo", "10", "token");

        assertNotNull(result);
        assertEquals(2, result.getFiles().size());
        assertEquals(3, result.getTotalAdditions());
        assertEquals(1, result.getTotalDeletions());
        assertEquals("src/Main.java", result.getFiles().get(0).getFilename());
        assertEquals("modified", result.getFiles().get(0).getStatus());
        assertEquals("src/New.java", result.getFiles().get(1).getFilename());
        assertEquals("added", result.getFiles().get(1).getStatus());
    }

    @Test
    void should_getDiff_when_deletedFile() {
        Map<String, Object> change = new HashMap<>();
        change.put("new_path", "src/Deleted.java");
        change.put("new_file", false);
        change.put("deleted_file", true);
        change.put("diff", "-removed line 1\n-removed line 2");

        Map<String, Object> apiResponse = Map.of("changes", List.of(change));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("owner", "repo", "10", "token");

        assertEquals(1, result.getFiles().size());
        assertEquals("deleted", result.getFiles().get(0).getStatus());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(2, result.getTotalDeletions());
    }

    @Test
    void should_returnEmptyDiff_when_responseIsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        DiffResult result = adapter.getDiff("owner", "repo", "10", "token");

        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
        assertEquals(0, result.getTotalAdditions());
        assertEquals(0, result.getTotalDeletions());
    }

    @Test
    void should_returnEmptyDiff_when_changesKeyIsNull() {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("changes", null);

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("owner", "repo", "10", "token");

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
                () -> adapter.getDiff("owner", "repo", "10", "token"));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_requestReviewers_successfully() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> adapter.requestReviewers("owner", "repo", "10",
                List.of("123", "456"), "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_requestReviewersApiError() {
        setupPutChain();
        when(responseSpec.bodyToMono(Void.class))
                .thenThrow(WebClientResponseException.create(
                        422, "Unprocessable Entity", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.requestReviewers("owner", "repo", "10",
                        List.of("123"), "token"));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_getPullRequest_when_opened() {
        Map<String, Object> apiResponse = Map.of(
                "iid", 10,
                "web_url", "https://gitlab.com/owner/repo/-/merge_requests/10",
                "title", "Test MR",
                "state", "opened",
                "source_branch", "feature/test",
                "target_branch", "main"
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "10", "token");

        assertNotNull(result);
        assertEquals("GITLAB", result.getPlatform());
        assertEquals("10", result.getExternalPrId());
        assertEquals("OPEN", result.getStatus());
        assertEquals("feature/test", result.getSourceBranch());
        assertEquals("main", result.getTargetBranch());
    }

    @Test
    void should_getPullRequest_when_merged() {
        Map<String, Object> apiResponse = Map.of(
                "iid", 10,
                "web_url", "https://gitlab.com/owner/repo/-/merge_requests/10",
                "title", "Test MR",
                "state", "merged",
                "source_branch", "feature/test",
                "target_branch", "main"
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "10", "token");

        assertEquals("MERGED", result.getStatus());
    }

    @Test
    void should_getPullRequest_when_closed() {
        Map<String, Object> apiResponse = Map.of(
                "iid", 10,
                "web_url", "https://gitlab.com/owner/repo/-/merge_requests/10",
                "title", "Test MR",
                "state", "closed",
                "source_branch", "feature/test",
                "target_branch", "main"
        );

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        PullRequestDto result = adapter.getPullRequest("owner", "repo", "10", "token");

        assertEquals("CLOSED", result.getStatus());
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestReturnsNull() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("owner", "repo", "10", "token"));
    }

    @Test
    void should_throwPlatformIntegrationException_when_getPullRequestApiError() {
        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenThrow(WebClientResponseException.create(
                        404, "Not Found", null, null, null));

        PlatformIntegrationException ex = assertThrows(PlatformIntegrationException.class,
                () -> adapter.getPullRequest("owner", "repo", "10", "token"));
        assertEquals("GITLAB", ex.getPlatform());
    }

    @Test
    void should_getDiff_when_diffIsNull() {
        Map<String, Object> change = new HashMap<>();
        change.put("new_path", "src/NoDiff.java");
        change.put("new_file", false);
        change.put("deleted_file", false);
        change.put("diff", null);

        Map<String, Object> apiResponse = Map.of("changes", List.of(change));

        setupGetChain();
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        DiffResult result = adapter.getDiff("owner", "repo", "10", "token");

        assertEquals(1, result.getFiles().size());
        assertEquals(0, result.getFiles().get(0).getAdditions());
        assertEquals(0, result.getFiles().get(0).getDeletions());
        assertNull(result.getFiles().get(0).getPatch());
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
