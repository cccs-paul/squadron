package com.squadron.agent.tool.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private GitClient gitClient;

    @BeforeEach
    void setUp() {
        gitClient = new GitClient(webClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_resolveStrategy_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "strategyType", "TRUNK_BASED",
                        "branchPrefix", "squadron/",
                        "targetBranch", "main",
                        "branchNameTemplate", "{prefix}{taskId}/{slug}"
                )
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        GitClient.BranchStrategyResponse result = gitClient.resolveStrategy(tenantId, projectId);

        assertNotNull(result);
        assertEquals("TRUNK_BASED", result.getStrategyType());
        assertEquals("squadron/", result.getBranchPrefix());
        assertEquals("main", result.getTargetBranch());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_generateBranchName_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", "squadron/abcd1234/fix-bug"
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        String result = gitClient.generateBranchName(tenantId, null, taskId, "Fix bug");

        assertNotNull(result);
        assertEquals("squadron/abcd1234/fix-bug", result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_createPullRequest_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        GitClient.CreatePrRequest request = GitClient.CreatePrRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .title("Fix login bug")
                .description("Fixes the login bug")
                .sourceBranch("squadron/abcd1234/fix-login-bug")
                .targetBranch("main")
                .accessToken("token")
                .build();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "externalPrId", "42",
                        "externalPrUrl", "https://github.com/owner/repo/pull/42",
                        "status", "OPEN"
                )
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        GitClient.PullRequestResponse result = gitClient.createPullRequest(request);

        assertNotNull(result);
        assertEquals("42", result.getPrNumber());
        assertEquals("https://github.com/owner/repo/pull/42", result.getUrl());
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_resolveStrategyFails() {
        UUID tenantId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500, "Internal Server Error", null,
                        "server error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.resolveStrategy(tenantId, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_generateBranchNameGetsEmptyResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Map<String, Object> emptyResponse = Map.of("success", false);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(emptyResponse));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.generateBranchName(tenantId, null, taskId, "Test"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_createPrGetsEmptyResponse() {
        GitClient.CreatePrRequest request = GitClient.CreatePrRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .title("PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();

        Map<String, Object> emptyResponse = Map.of("success", false);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(emptyResponse));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.createPullRequest(request));
    }

    // ---- Tests for getPullRequestByTaskId ----

    @Test
    @SuppressWarnings("unchecked")
    void should_getPullRequestByTaskId_successfully() {
        UUID taskId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "id", prId,
                        "externalPrId", "42",
                        "externalPrUrl", "https://github.com/owner/repo/pull/42",
                        "status", "OPEN"
                )
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        GitClient.PullRequestResponse result = gitClient.getPullRequestByTaskId(taskId);

        assertNotNull(result);
        assertEquals(prId, result.getId());
        assertEquals("42", result.getPrNumber());
        assertEquals("https://github.com/owner/repo/pull/42", result.getUrl());
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_getPullRequestByTaskIdFails() {
        UUID taskId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404, "Not Found", null,
                        "not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.getPullRequestByTaskId(taskId));
    }

    // ---- Tests for checkMergeability ----

    @Test
    @SuppressWarnings("unchecked")
    void should_checkMergeability_successfully_when_mergeable() {
        String prId = UUID.randomUUID().toString();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "mergeable", true,
                        "conflictFiles", List.of()
                )
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        GitClient.MergeabilityResponse result = gitClient.checkMergeability(prId);

        assertNotNull(result);
        assertTrue(result.isMergeable());
        assertTrue(result.getConflictFiles().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_checkMergeability_when_notMergeable() {
        String prId = UUID.randomUUID().toString();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", Map.of(
                        "mergeable", false,
                        "conflictFiles", List.of("src/Main.java", "pom.xml")
                )
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        GitClient.MergeabilityResponse result = gitClient.checkMergeability(prId);

        assertNotNull(result);
        assertFalse(result.isMergeable());
        assertEquals(2, result.getConflictFiles().size());
        assertEquals("src/Main.java", result.getConflictFiles().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_checkMergeabilityFails() {
        String prId = UUID.randomUUID().toString();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500, "Internal Server Error", null,
                        "error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.checkMergeability(prId));
    }

    // ---- Tests for mergePullRequest ----

    @Test
    @SuppressWarnings("unchecked")
    void should_mergePullRequest_successfully() {
        String prId = UUID.randomUUID().toString();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> gitClient.mergePullRequest(prId, "MERGE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_mergePullRequest_withNullStrategy_defaultsToMerge() {
        String prId = UUID.randomUUID().toString();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> gitClient.mergePullRequest(prId, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_mergePullRequestFails() {
        String prId = UUID.randomUUID().toString();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        409, "Conflict", null,
                        "merge conflict".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(GitClient.GitClientException.class,
                () -> gitClient.mergePullRequest(prId, "MERGE"));
    }
}
