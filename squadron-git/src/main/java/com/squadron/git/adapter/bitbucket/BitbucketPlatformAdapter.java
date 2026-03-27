package com.squadron.git.adapter.bitbucket;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git platform adapter for Bitbucket using the Bitbucket REST API 2.0.
 * API base: https://api.bitbucket.org
 */
@Component
public class BitbucketPlatformAdapter implements GitPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(BitbucketPlatformAdapter.class);
    private static final String PLATFORM_TYPE = "BITBUCKET";
    private static final String BASE_URL = "https://api.bitbucket.org";

    private final WebClient webClient;

    public BitbucketPlatformAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PullRequestDto createPullRequest(CreatePullRequestRequest request) {
        log.info("Creating Bitbucket pull request for {}/{}: {}", request.getRepoOwner(), request.getRepoName(), request.getTitle());
        try {
            Map<String, Object> source = new HashMap<>();
            source.put("branch", Map.of("name", request.getSourceBranch()));

            Map<String, Object> destination = new HashMap<>();
            destination.put("branch", Map.of("name", request.getTargetBranch()));

            Map<String, Object> body = new HashMap<>();
            body.put("title", request.getTitle());
            body.put("source", source);
            body.put("destination", destination);
            if (request.getDescription() != null) {
                body.put("description", request.getDescription());
            }

            if (request.getReviewers() != null && !request.getReviewers().isEmpty()) {
                List<Map<String, Object>> reviewerList = request.getReviewers().stream()
                        .map(username -> {
                            Map<String, Object> reviewer = new HashMap<>();
                            reviewer.put("username", username);
                            return reviewer;
                        })
                        .toList();
                body.put("reviewers", reviewerList);
            }

            Map<String, Object> response = webClient.post()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests",
                            request.getRepoOwner(), request.getRepoName())
                    .header("Authorization", "Bearer " + request.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from Bitbucket API");
            }

            Map<String, Object> links = (Map<String, Object>) response.get("links");
            Map<String, Object> htmlLink = links != null ? (Map<String, Object>) links.get("html") : null;
            String htmlUrl = htmlLink != null ? (String) htmlLink.get("href") : null;

            PullRequestDto dto = PullRequestDto.builder()
                    .tenantId(request.getTenantId())
                    .taskId(request.getTaskId())
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("id")))
                    .externalPrUrl(htmlUrl)
                    .title((String) response.get("title"))
                    .sourceBranch(request.getSourceBranch())
                    .targetBranch(request.getTargetBranch())
                    .status("OPEN")
                    .build();

            log.info("Created Bitbucket PR #{} for {}/{}", dto.getExternalPrId(), request.getRepoOwner(), request.getRepoName());
            return dto;
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error creating PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to create pull request: " + e.getMessage(), e);
        }
    }

    @Override
    public void mergePullRequest(String owner, String repo, String prId, String mergeStrategy, String accessToken) {
        log.info("Merging Bitbucket PR #{} for {}/{} with strategy {}", prId, owner, repo, mergeStrategy);
        try {
            Map<String, Object> body = new HashMap<>();
            String strategy = switch (mergeStrategy.toUpperCase()) {
                case "SQUASH" -> "squash";
                case "REBASE" -> "fast_forward";
                default -> "merge_commit";
            };
            body.put("merge_strategy", strategy);

            webClient.post()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}/merge",
                            owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Merged Bitbucket PR #{} for {}/{}", prId, owner, repo);
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error merging PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to merge pull request: " + e.getMessage(), e);
        }
    }

    @Override
    public void addReviewComment(String owner, String repo, String prId, String body, String accessToken) {
        log.info("Adding comment to Bitbucket PR #{} for {}/{}", prId, owner, repo);
        try {
            Map<String, Object> requestBody = Map.of(
                    "content", Map.of("raw", body)
            );

            webClient.post()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}/comments",
                            owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Added comment to Bitbucket PR #{}", prId);
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error adding comment: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to add review comment: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public DiffResult getDiff(String owner, String repo, String prId, String accessToken) {
        log.info("Getting diffstat for Bitbucket PR #{} for {}/{}", prId, owner, repo);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}/diffstat",
                            owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("values") == null) {
                return DiffResult.builder()
                        .files(Collections.emptyList())
                        .totalAdditions(0)
                        .totalDeletions(0)
                        .build();
            }

            List<Map<String, Object>> values = (List<Map<String, Object>>) response.get("values");
            List<DiffFile> diffFiles = new ArrayList<>();
            int totalAdditions = 0;
            int totalDeletions = 0;

            for (Map<String, Object> value : values) {
                int additions = value.get("lines_added") instanceof Number n ? n.intValue() : 0;
                int deletions = value.get("lines_removed") instanceof Number n ? n.intValue() : 0;
                totalAdditions += additions;
                totalDeletions += deletions;

                String statusStr = (String) value.get("status");
                String status = switch (statusStr != null ? statusStr : "") {
                    case "added" -> "added";
                    case "removed" -> "deleted";
                    default -> "modified";
                };

                Map<String, Object> newFile = (Map<String, Object>) value.get("new");
                String filename = newFile != null ? (String) newFile.get("path") : "unknown";

                diffFiles.add(DiffFile.builder()
                        .filename(filename)
                        .status(status)
                        .additions(additions)
                        .deletions(deletions)
                        .build());
            }

            return DiffResult.builder()
                    .files(diffFiles)
                    .totalAdditions(totalAdditions)
                    .totalDeletions(totalDeletions)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error getting diffstat: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get diff: " + e.getMessage(), e);
        }
    }

    @Override
    public void requestReviewers(String owner, String repo, String prId, List<String> reviewers, String accessToken) {
        log.info("Requesting reviewers for Bitbucket PR #{} for {}/{}", prId, owner, repo);
        try {
            // Bitbucket sets reviewers via updating the PR
            List<Map<String, Object>> reviewerList = reviewers.stream()
                    .map(username -> {
                        Map<String, Object> reviewer = new HashMap<>();
                        reviewer.put("username", username);
                        return reviewer;
                    })
                    .toList();

            Map<String, Object> body = Map.of("reviewers", reviewerList);

            webClient.put()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}",
                            owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Requested reviewers for Bitbucket PR #{}", prId);
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error requesting reviewers: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to request reviewers: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PullRequestDto getPullRequest(String owner, String repo, String prId, String accessToken) {
        log.info("Getting Bitbucket PR #{} for {}/{}", prId, owner, repo);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/2.0/repositories/{workspace}/{repo_slug}/pullrequests/{id}",
                            owner, repo, prId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                throw new PlatformIntegrationException(PLATFORM_TYPE, "Empty response from Bitbucket API");
            }

            String state = (String) response.get("state");
            String status = switch (state != null ? state : "") {
                case "MERGED" -> "MERGED";
                case "DECLINED" -> "CLOSED";
                case "SUPERSEDED" -> "CLOSED";
                default -> "OPEN";
            };

            Map<String, Object> sourceBranch = null;
            Map<String, Object> destBranch = null;
            Map<String, Object> sourceObj = (Map<String, Object>) response.get("source");
            Map<String, Object> destObj = (Map<String, Object>) response.get("destination");
            if (sourceObj != null) {
                sourceBranch = (Map<String, Object>) sourceObj.get("branch");
            }
            if (destObj != null) {
                destBranch = (Map<String, Object>) destObj.get("branch");
            }

            Map<String, Object> links = (Map<String, Object>) response.get("links");
            Map<String, Object> htmlLink = links != null ? (Map<String, Object>) links.get("html") : null;
            String htmlUrl = htmlLink != null ? (String) htmlLink.get("href") : null;

            return PullRequestDto.builder()
                    .platform(PLATFORM_TYPE)
                    .externalPrId(String.valueOf(response.get("id")))
                    .externalPrUrl(htmlUrl)
                    .title((String) response.get("title"))
                    .sourceBranch(sourceBranch != null ? (String) sourceBranch.get("name") : null)
                    .targetBranch(destBranch != null ? (String) destBranch.get("name") : null)
                    .status(status)
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Bitbucket API error getting PR: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PlatformIntegrationException(PLATFORM_TYPE,
                    "Failed to get pull request: " + e.getMessage(), e);
        }
    }
}
