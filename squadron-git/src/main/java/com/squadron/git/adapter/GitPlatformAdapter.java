package com.squadron.git.adapter;

import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.PullRequestDto;

import java.util.List;

/**
 * Adapter interface for Git platform operations (GitHub, GitLab, Bitbucket).
 * Each implementation handles the platform-specific API calls.
 */
public interface GitPlatformAdapter {

    /**
     * Returns the platform type identifier (e.g. GITHUB, GITLAB, BITBUCKET).
     */
    String getPlatformType();

    /**
     * Creates a pull request / merge request on the platform.
     */
    PullRequestDto createPullRequest(CreatePullRequestRequest request);

    /**
     * Merges an existing pull request / merge request.
     */
    void mergePullRequest(String owner, String repo, String prId, String mergeStrategy, String accessToken);

    /**
     * Adds a review comment to a pull request / merge request.
     */
    void addReviewComment(String owner, String repo, String prId, String body, String accessToken);

    /**
     * Retrieves the diff for a pull request / merge request.
     */
    DiffResult getDiff(String owner, String repo, String prId, String accessToken);

    /**
     * Requests reviewers for a pull request / merge request.
     */
    void requestReviewers(String owner, String repo, String prId, List<String> reviewers, String accessToken);

    /**
     * Retrieves the current state of a pull request / merge request.
     */
    PullRequestDto getPullRequest(String owner, String repo, String prId, String accessToken);
}
