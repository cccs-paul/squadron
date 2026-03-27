package com.squadron.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for communicating with the squadron-git service.
 * Provides methods to resolve branch strategies, generate branch names,
 * and create pull requests.
 */
@Service
public class GitClient {

    private static final Logger log = LoggerFactory.getLogger(GitClient.class);

    private final WebClient webClient;

    public GitClient(@Value("${squadron.git.url:http://localhost:8086}") String gitUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(gitUrl)
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built WebClient.
     */
    GitClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Resolve the effective branch strategy for a tenant/project.
     */
    public BranchStrategyResponse resolveStrategy(UUID tenantId, UUID projectId) {
        log.debug("Resolving branch strategy for tenant {} project {}", tenantId, projectId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/git/branch-strategies/resolve")
                                .queryParam("tenantId", tenantId.toString());
                        if (projectId != null) {
                            uriBuilder.queryParam("projectId", projectId.toString());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return extractStrategyFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to resolve branch strategy: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to resolve branch strategy: " + e.getStatusCode(), e);
        }
    }

    /**
     * Generate a branch name using the resolved strategy.
     */
    public String generateBranchName(UUID tenantId, UUID projectId, UUID taskId, String taskTitle) {
        log.debug("Generating branch name for tenant {} project {} task {}", tenantId, projectId, taskId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/git/branch-strategies/generate-name")
                                .queryParam("tenantId", tenantId.toString())
                                .queryParam("taskId", taskId.toString())
                                .queryParam("taskTitle", taskTitle);
                        if (projectId != null) {
                            uriBuilder.queryParam("projectId", projectId.toString());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.get("data") != null) {
                return response.get("data").toString();
            }
            throw new GitClientException("Empty response from generate-name endpoint");
        } catch (WebClientResponseException e) {
            log.error("Failed to generate branch name: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to generate branch name: " + e.getStatusCode(), e);
        }
    }

    /**
     * Create a pull request via the squadron-git service.
     */
    public PullRequestResponse createPullRequest(CreatePrRequest request) {
        log.debug("Creating pull request for task {} on platform {}", request.getTaskId(), request.getPlatform());

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/git/pull-requests")
                    .bodyValue(request.toRequestMap())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return extractPrFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to create pull request: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to create pull request: " + e.getStatusCode(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private BranchStrategyResponse extractStrategyFromResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            throw new GitClientException("Empty response from branch strategy endpoint");
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return BranchStrategyResponse.builder()
                .strategyType(getStringValue(data, "strategyType"))
                .branchPrefix(getStringValue(data, "branchPrefix"))
                .targetBranch(getStringValue(data, "targetBranch"))
                .developmentBranch(getStringValue(data, "developmentBranch"))
                .branchNameTemplate(getStringValue(data, "branchNameTemplate"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private PullRequestResponse extractPrFromResponse(Map<String, Object> response) {
        if (response == null || response.get("data") == null) {
            throw new GitClientException("Empty response from pull request endpoint");
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return PullRequestResponse.builder()
                .id(getStringValue(data, "id"))
                .prNumber(getStringValue(data, "externalPrId"))
                .url(getStringValue(data, "externalPrUrl"))
                .status(getStringValue(data, "status"))
                .build();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Branch strategy response from the git service.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BranchStrategyResponse {
        private String strategyType;
        private String branchPrefix;
        private String targetBranch;
        private String developmentBranch;
        private String branchNameTemplate;
    }

    /**
     * Request to create a pull request.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreatePrRequest {
        private UUID tenantId;
        private UUID taskId;
        private String platform;
        private String repoOwner;
        private String repoName;
        private String title;
        private String description;
        private String sourceBranch;
        private String targetBranch;
        private String accessToken;

        public Map<String, Object> toRequestMap() {
            return Map.ofEntries(
                    Map.entry("tenantId", tenantId.toString()),
                    Map.entry("taskId", taskId.toString()),
                    Map.entry("platform", platform),
                    Map.entry("repoOwner", repoOwner != null ? repoOwner : ""),
                    Map.entry("repoName", repoName != null ? repoName : ""),
                    Map.entry("title", title),
                    Map.entry("description", description != null ? description : ""),
                    Map.entry("sourceBranch", sourceBranch),
                    Map.entry("targetBranch", targetBranch),
                    Map.entry("accessToken", accessToken != null ? accessToken : "")
            );
        }
    }

    /**
     * Pull request response from the git service.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PullRequestResponse {
        private String id;
        private String prNumber;
        private String url;
        private String status;
    }

    /**
     * Look up the pull request record for a task.
     */
    public PullRequestResponse getPullRequestByTaskId(UUID taskId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/git/pull-requests/task/{taskId}", taskId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            return extractPrFromResponse(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to get PR for task {}: {} {}", taskId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to get PR for task: " + e.getStatusCode(), e);
        }
    }

    /**
     * Check if a pull request is mergeable (no conflicts).
     */
    public MergeabilityResponse checkMergeability(String prRecordId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/git/pull-requests/{id}/mergeability", prRecordId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            if (response == null || response.get("data") == null) {
                throw new GitClientException("Empty mergeability response");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            return MergeabilityResponse.builder()
                    .mergeable(Boolean.TRUE.equals(data.get("mergeable")))
                    .conflictFiles(data.get("conflictFiles") instanceof List<?> list
                            ? list.stream().map(Object::toString).toList()
                            : List.of())
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Failed to check mergeability: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to check mergeability: " + e.getStatusCode(), e);
        }
    }

    /**
     * Merge a pull request via the git service.
     */
    public void mergePullRequest(String prRecordId, String mergeStrategy) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "pullRequestRecordId", prRecordId,
                    "mergeStrategy", mergeStrategy != null ? mergeStrategy : "MERGE"
            );
            webClient.post()
                    .uri("/api/git/pull-requests/{id}/merge", prRecordId)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
            log.info("Successfully merged PR {}", prRecordId);
        } catch (WebClientResponseException e) {
            log.error("Failed to merge PR {}: {} {}", prRecordId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GitClientException("Failed to merge PR: " + e.getStatusCode(), e);
        }
    }

    /**
     * Mergeability check response from the git service.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MergeabilityResponse {
        private boolean mergeable;
        @lombok.Builder.Default
        private List<String> conflictFiles = List.of();
    }

    /**
     * Exception thrown when git service communication fails.
     */
    public static class GitClientException extends RuntimeException {
        public GitClientException(String message) {
            super(message);
        }

        public GitClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
