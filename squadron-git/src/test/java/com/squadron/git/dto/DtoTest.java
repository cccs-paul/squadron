package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void should_createCloneRequest() {
        UUID workspaceId = UUID.randomUUID();
        CloneRequest request = CloneRequest.builder()
                .workspaceId(workspaceId)
                .repoUrl("https://github.com/owner/repo.git")
                .branch("main")
                .accessToken("token123")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("https://github.com/owner/repo.git", request.getRepoUrl());
        assertEquals("main", request.getBranch());
        assertEquals("token123", request.getAccessToken());
    }

    @Test
    void should_createBranchRequest() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/new")
                .baseBranch("main")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("feature/new", request.getBranchName());
        assertEquals("main", request.getBaseBranch());
    }

    @Test
    void should_createCommitRequest() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug")
                .authorName("John Doe")
                .authorEmail("john@example.com")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("Fix bug", request.getMessage());
        assertEquals("John Doe", request.getAuthorName());
        assertEquals("john@example.com", request.getAuthorEmail());
    }

    @Test
    void should_createPushRequest_withDefaults() {
        UUID workspaceId = UUID.randomUUID();
        PushRequest request = PushRequest.builder()
                .workspaceId(workspaceId)
                .branch("main")
                .accessToken("token123")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("origin", request.getRemoteName());
        assertEquals("main", request.getBranch());
        assertEquals("token123", request.getAccessToken());
    }

    @Test
    void should_createCreatePullRequestRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .title("My PR")
                .description("Description")
                .sourceBranch("feature")
                .targetBranch("main")
                .reviewers(List.of("user1", "user2"))
                .accessToken("token")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("GITHUB", request.getPlatform());
        assertEquals("owner", request.getRepoOwner());
        assertEquals("repo", request.getRepoName());
        assertEquals("My PR", request.getTitle());
        assertEquals("Description", request.getDescription());
        assertEquals("feature", request.getSourceBranch());
        assertEquals("main", request.getTargetBranch());
        assertEquals(2, request.getReviewers().size());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_createMergeRequest_withDefaults() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .accessToken("token")
                .build();

        assertEquals(prId, request.getPullRequestRecordId());
        assertEquals("MERGE", request.getMergeStrategy());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_createPullRequestDto() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        PullRequestDto dto = PullRequestDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("PR Title")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .build();

        assertEquals(id, dto.getId());
        assertEquals("GITHUB", dto.getPlatform());
        assertEquals("42", dto.getExternalPrId());
        assertEquals("OPEN", dto.getStatus());
    }

    @Test
    void should_createDiffResult() {
        DiffFile file1 = DiffFile.builder()
                .filename("src/main.java")
                .status("modified")
                .additions(10)
                .deletions(3)
                .patch("@@ -1,3 +1,10 @@")
                .build();

        DiffFile file2 = DiffFile.builder()
                .filename("README.md")
                .status("added")
                .additions(5)
                .deletions(0)
                .build();

        DiffResult result = DiffResult.builder()
                .files(List.of(file1, file2))
                .totalAdditions(15)
                .totalDeletions(3)
                .build();

        assertEquals(2, result.getFiles().size());
        assertEquals(15, result.getTotalAdditions());
        assertEquals(3, result.getTotalDeletions());
    }

    @Test
    void should_createDiffFile() {
        DiffFile file = DiffFile.builder()
                .filename("test.java")
                .status("deleted")
                .additions(0)
                .deletions(50)
                .patch("patch content")
                .build();

        assertEquals("test.java", file.getFilename());
        assertEquals("deleted", file.getStatus());
        assertEquals(0, file.getAdditions());
        assertEquals(50, file.getDeletions());
        assertEquals("patch content", file.getPatch());
    }

    @Test
    void should_createGitCommandResult() {
        GitCommandResult result = GitCommandResult.builder()
                .success(true)
                .output("Already up to date.")
                .errorOutput("")
                .exitCode(0)
                .build();

        assertTrue(result.isSuccess());
        assertEquals("Already up to date.", result.getOutput());
        assertEquals("", result.getErrorOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void should_createGitCommandResult_failure() {
        GitCommandResult result = GitCommandResult.builder()
                .success(false)
                .output("")
                .errorOutput("fatal: not a git repository")
                .exitCode(128)
                .build();

        assertFalse(result.isSuccess());
        assertEquals("fatal: not a git repository", result.getErrorOutput());
        assertEquals(128, result.getExitCode());
    }
}
