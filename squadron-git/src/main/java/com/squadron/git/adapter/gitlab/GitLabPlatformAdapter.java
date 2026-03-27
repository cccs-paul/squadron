package com.squadron.git.adapter.gitlab;

import com.squadron.common.exception.PlatformIntegrationException;
import com.squadron.git.adapter.GitPlatformAdapter;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffFile;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.PullRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git platform adapter for GitLab using the GitLab REST API v4.
 * API base: https://gitlab.com/api/v4
 */
@Component
public class GitLabPlatformAdapter implements GitPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitLabPlatformAdapter.class);
    private static final String PLATFORM_TYPE = "GITLAB";
    private static final String BASE_URL = "https://gitlab.com";

    private final WebClient webClient;

    public GitLabPlatformAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    private String encodeProjectId(String owner, String repo) {
        return URLEncoder.encode(owner + "/" + repo, StandardCharsets.UTF_8);
    }

    @Override
    public PullRequestDto createPullRequest(CreatePullRequestRequest request) {
        log.info("Creating GitLab merge request for {}/{}: {}", request.getRepoOwner(), request.getRepoName(), request.getTitle());
        try {
            String projectId = encodeProjectId(request.getRepoOwner(), request.getRepoName());

            Map<String, Object> body = new HashMap<>();
            body.put("title", request.getTitle());
            body.put("source_branch", request.getSourceBranch());
            body.put("target_branch", request.getTargetBranch());
            if (request.getDescription() != null) {
                body.put("description", request.getDescription());
            }

            Map<String, Object> response = webClient.post()
                    .uri("/api/v4/projects/{id}/merge_requests", projectId)
                    .header("PRIVATE-TOKEN", request.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from GitLab API");
            }

            PullRequestDto dto = PullRequestDto.builder()
                    .tenantId(request.getTenantId())
                    .taskId(request.getTaskId())
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("iid")))
                    .externalPrUrl((String) response.get("web_url"))
                    .title((String) response.get("title"))
                    .sourceBranch(request.getSourceBranch())
                    .targetBranch(request.getTargetBranch())
                    .status("OPEN")
                    .build();

            log.info("Created GitLab MR !{} for {}/{}", dto.getExternalPrId(), request.getRepoOwner(), request.getRepoName());
            return dto;
        } catch (WebClientResponseException e) {
            log.error("GitLab API error creating MR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to create merge request: " + e.getMessage(), e);
        }
    }

    @Override
    public void mergePullRequest(String owner, String repo, String prId, String mergeStrategy, String accessToken) {
        log.info("Merging GitLab MR !{} for {}/{} with strategy {}", prId, owner, repo, mergeStrategy);
        try {
            String projectId = encodeProjectId(owner, repo);

            Map<String, Object> body = new HashMap<>();
            if ("SQUASH".equalsIgnoreCase(mergeStrategy)) {
                body.put("squash", true);
            }

            webClient.put()
                    .uri("/api/v4/projects/{id}/merge_requests/{mr_iid}/merge", projectId, prId)
                    .header("PRIVATE-TOKEN", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Merged GitLab MR !{} for {}/{}", prId, owner, repo);
        } catch (WebClientResponseException e) {
            log.error("GitLab API error merging MR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to merge merge request: " + e.getMessage(), e);
        }
    }

    @Override
    public void addReviewComment(String owner, String repo, String prId, String body, String accessToken) {
        log.info("Adding note to GitLab MR !{} for {}/{}", prId, owner, repo);
        try {
            String projectId = encodeProjectId(owner, repo);

            Map<String, Object> requestBody = Map.of("body", body);

            webClient.post()
                    .uri("/api/v4/projects/{id}/merge_requests/{mr_iid}/notes", projectId, prId)
                    .header("PRIVATE-TOKEN", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Added note to GitLab MR !{}", prId);
        } catch (WebClientResponseException e) {
            log.error("GitLab API error adding note: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to add review comment: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DiffResult getDiff(String owner, String repo, String prId, String accessToken) {
        log.info("Getting diff for GitLab MR !{} for {}/{}", prId, owner, repo);
        try {
            String projectId = encodeProjectId(owner, repo);

            Map<String, Object> response = webClient.get()
                    .uri("/api/v4/projects/{id}/merge_requests/{mr_iid}/changes", projectId, prId)
                    .header("PRIVATE-TOKEN", accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("changes") == null) {
                return DiffResult.builder()
                        .files(Collections.emptyList())
                        .totalAdditions(0)
                        .totalDeletions(0)
                        .build();
            }

            List<Map<String, Object>> changes = (List<Map<String, Object>>) response.get("changes");
            List<DiffFile> diffFiles = new ArrayList<>();
            int totalAdditions = 0;
            int totalDeletions = 0;

            for (Map<String, Object> change : changes) {
                String diff = (String) change.get("diff");
                int additions = 0;
                int deletions = 0;
                if (diff != null) {
                    for (String line : diff.split("\n")) {
                        if (line.startsWith("+") && !line.startsWith("+++")) {
                            additions++;
                        } else if (line.startsWith("-") && !line.startsWith("---")) {
                            deletions++;
                        }
                    }
                }
                totalAdditions += additions;
                totalDeletions += deletions;

                boolean newFile = Boolean.TRUE.equals(change.get("new_file"));
                boolean deletedFile = Boolean.TRUE.equals(change.get("deleted_file"));
                String status = newFile ? "added" : deletedFile ? "deleted" : "modified";

                diffFiles.add(DiffFile.builder()
                        .filename((String) change.get("new_path"))
                        .status(status)
                        .additions(additions)
                        .deletions(deletions)
                        .patch(diff)
                        .build());
            }

            return DiffResult.builder()
                    .files(diffFiles)
                    .totalAdditions(totalAdditions)
                    .totalDeletions(totalDeletions)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("GitLab API error getting diff: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get diff: " + e.getMessage(), e);
        }
    }

    @Override
    public void requestReviewers(String owner, String repo, String prId, List<String> reviewers, String accessToken) {
        log.info("Requesting reviewers for GitLab MR !{} for {}/{}", prId, owner, repo);
        try {
            String projectId = encodeProjectId(owner, repo);

            // GitLab uses reviewer_ids (numeric user IDs) on the merge request itself
            Map<String, Object> body = Map.of("reviewer_ids", reviewers);

            webClient.put()
                    .uri("/api/v4/projects/{id}/merge_requests/{mr_iid}", projectId, prId)
                    .header("PRIVATE-TOKEN", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Requested reviewers for GitLab MR !{}", prId);
        } catch (WebClientResponseException e) {
            log.error("GitLab API error requesting reviewers: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to request reviewers: " + e.getMessage(), e);
        }
    }

    @Override
    public PullRequestDto getPullRequest(String owner, String repo, String prId, String accessToken) {
        log.info("Getting GitLab MR !{} for {}/{}", prId, owner, repo);
        try {
            String projectId = encodeProjectId(owner, repo);

            Map<String, Object> response = webClient.get()
                    .uri("/api/v4/projects/{id}/merge_requests/{mr_iid}", projectId, prId)
                    .header("PRIVATE-TOKEN", accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from GitLab API");
            }

            String state = (String) response.get("state");
            String status = switch (state != null ? state : "") {
                case "merged" -> "MERGED";
                case "closed" -> "CLOSED";
                default -> "OPEN";
            };

            return PullRequestDto.builder()
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("iid")))
                    .externalPrUrl((String) response.get("web_url"))
                    .title((String) response.get("title"))
                    .sourceBranch((String) response.get("source_branch"))
                    .targetBranch((String) response.get("target_branch"))
                    .status(status)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("GitLab API error getting MR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get merge request: " + e.getMessage(), e);
        }
    }
}
