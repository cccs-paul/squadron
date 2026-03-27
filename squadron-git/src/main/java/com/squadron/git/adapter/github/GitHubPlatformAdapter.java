package com.squadron.git.adapter.github;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Git platform adapter for GitHub using the GitHub REST API.
 * API base: https://api.github.com
 */
@Component
public class GitHubPlatformAdapter implements GitPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubPlatformAdapter.class);
    private static final String PLATFORM_TYPE = "GITHUB";
    private static final String BASE_URL = "https://api.github.com";

    private final WebClient webClient;

    public GitHubPlatformAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public PullRequestDto createPullRequest(CreatePullRequestRequest request) {
        log.info("Creating GitHub pull request for {}/{}: {}", request.getRepoOwner(), request.getRepoName(), request.getTitle());
        try {
            Map<String, Object> body = Map.of(
                    "title", request.getTitle(),
                    "body", request.getDescription() != null ? request.getDescription() : "",
                    "head", request.getSourceBranch(),
                    "base", request.getTargetBranch()
            );

            Map<String, Object> response = webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls", request.getRepoOwner(), request.getRepoName())
                    .header("Authorization", "Bearer " + request.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from GitHub API");
            }

            PullRequestDto dto = PullRequestDto.builder()
                    .tenantId(request.getTenantId())
                    .taskId(request.getTaskId())
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("number")))
                    .externalPrUrl((String) response.get("html_url"))
                    .title((String) response.get("title"))
                    .sourceBranch(request.getSourceBranch())
                    .targetBranch(request.getTargetBranch())
                    .status("OPEN")
                    .build();

            log.info("Created GitHub PR #{} for {}/{}", dto.getExternalPrId(), request.getRepoOwner(), request.getRepoName());
            return dto;
        } catch (WebClientResponseException e) {
            log.error("GitHub API error creating PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to create pull request: " + e.getMessage(), e);
        }
    }

    @Override
    public void mergePullRequest(String owner, String repo, String prId, String mergeStrategy, String accessToken) {
        log.info("Merging GitHub PR #{} for {}/{} with strategy {}", prId, owner, repo, mergeStrategy);
        try {
            String mergeMethod = switch (mergeStrategy.toUpperCase()) {
                case "SQUASH" -> "squash";
                case "REBASE" -> "rebase";
                default -> "merge";
            };

            Map<String, Object> body = Map.of("merge_method", mergeMethod);

            webClient.put()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/merge", owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Merged GitHub PR #{} for {}/{}", prId, owner, repo);
        } catch (WebClientResponseException e) {
            log.error("GitHub API error merging PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to merge pull request: " + e.getMessage(), e);
        }
    }

    @Override
    public void addReviewComment(String owner, String repo, String prId, String body, String accessToken) {
        log.info("Adding review comment to GitHub PR #{} for {}/{}", prId, owner, repo);
        try {
            Map<String, Object> requestBody = Map.of(
                    "body", body,
                    "event", "COMMENT"
            );

            webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/reviews", owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Added review comment to GitHub PR #{}", prId);
        } catch (WebClientResponseException e) {
            log.error("GitHub API error adding review comment: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to add review comment: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DiffResult getDiff(String owner, String repo, String prId, String accessToken) {
        log.info("Getting diff for GitHub PR #{} for {}/{}", prId, owner, repo);
        try {
            List<Map<String, Object>> files = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/files", owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (files == null) {
                return DiffResult.builder()
                        .files(Collections.emptyList())
                        .totalAdditions(0)
                        .totalDeletions(0)
                        .build();
            }

            int totalAdditions = 0;
            int totalDeletions = 0;
            List<DiffFile> diffFiles = new ArrayList<>();

            for (Map<String, Object> file : files) {
                int additions = file.get("additions") instanceof Number n ? n.intValue() : 0;
                int deletions = file.get("deletions") instanceof Number n ? n.intValue() : 0;
                totalAdditions += additions;
                totalDeletions += deletions;

                diffFiles.add(DiffFile.builder()
                        .filename((String) file.get("filename"))
                        .status((String) file.get("status"))
                        .additions(additions)
                        .deletions(deletions)
                        .patch((String) file.get("patch"))
                        .build());
            }

            return DiffResult.builder()
                    .files(diffFiles)
                    .totalAdditions(totalAdditions)
                    .totalDeletions(totalDeletions)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("GitHub API error getting diff: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get diff: " + e.getMessage(), e);
        }
    }

    @Override
    public void requestReviewers(String owner, String repo, String prId, List<String> reviewers, String accessToken) {
        log.info("Requesting reviewers {} for GitHub PR #{} for {}/{}", reviewers, prId, owner, repo);
        try {
            Map<String, Object> body = Map.of("reviewers", reviewers);

            webClient.post()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}/requested_reviewers", owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Requested reviewers for GitHub PR #{}", prId);
        } catch (WebClientResponseException e) {
            log.error("GitHub API error requesting reviewers: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to request reviewers: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PullRequestDto getPullRequest(String owner, String repo, String prId, String accessToken) {
        log.info("Getting GitHub PR #{} for {}/{}", prId, owner, repo);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from GitHub API");
            }

            Map<String, Object> head = (Map<String, Object>) response.get("head");
            Map<String, Object> base = (Map<String, Object>) response.get("base");
            String state = (String) response.get("state");
            Boolean merged = (Boolean) response.get("merged");

            String status;
            if (Boolean.TRUE.equals(merged)) {
                status = "MERGED";
            } else if ("closed".equals(state)) {
                status = "CLOSED";
            } else {
                status = "OPEN";
            }

            return PullRequestDto.builder()
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("number")))
                    .externalPrUrl((String) response.get("html_url"))
                    .title((String) response.get("title"))
                    .sourceBranch(head != null ? (String) head.get("ref") : null)
                    .targetBranch(base != null ? (String) base.get("ref") : null)
                    .status(status)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("GitHub API error getting PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get pull request: " + e.getMessage(), e);
        }
    }
}
