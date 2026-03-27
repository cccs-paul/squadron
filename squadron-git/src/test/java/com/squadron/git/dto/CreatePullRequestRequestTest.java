package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreatePullRequestRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        List<String> reviewers = List.of("user1", "user2", "user3");

        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("squadron-dev")
                .repoName("squadron")
                .title("feat: Add metrics tracking")
                .description("This PR adds usage metrics tracking.")
                .sourceBranch("feature/metrics")
                .targetBranch("main")
                .reviewers(reviewers)
                .accessToken("ghp_token123")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("GITHUB", request.getPlatform());
        assertEquals("squadron-dev", request.getRepoOwner());
        assertEquals("squadron", request.getRepoName());
        assertEquals("feat: Add metrics tracking", request.getTitle());
        assertEquals("This PR adds usage metrics tracking.", request.getDescription());
        assertEquals("feature/metrics", request.getSourceBranch());
        assertEquals("main", request.getTargetBranch());
        assertEquals(3, request.getReviewers().size());
        assertEquals("ghp_token123", request.getAccessToken());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreatePullRequestRequest request = new CreatePullRequestRequest();
        assertNull(request.getTenantId());
        assertNull(request.getTaskId());
        assertNull(request.getPlatform());
        assertNull(request.getRepoOwner());
        assertNull(request.getRepoName());
        assertNull(request.getTitle());
        assertNull(request.getDescription());
        assertNull(request.getSourceBranch());
        assertNull(request.getTargetBranch());
        assertNull(request.getReviewers());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        List<String> reviewers = List.of("reviewer1");

        CreatePullRequestRequest request = new CreatePullRequestRequest(
                tenantId, taskId, "GITLAB", "owner", "repo",
                "Title", "Description", "source", "target",
                reviewers, "token");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("GITLAB", request.getPlatform());
        assertEquals("owner", request.getRepoOwner());
        assertEquals("repo", request.getRepoName());
        assertEquals("Title", request.getTitle());
        assertEquals("Description", request.getDescription());
        assertEquals("source", request.getSourceBranch());
        assertEquals("target", request.getTargetBranch());
        assertEquals(1, request.getReviewers().size());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_supportSettersAndGetters() {
        CreatePullRequestRequest request = new CreatePullRequestRequest();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        request.setTenantId(tenantId);
        request.setTaskId(taskId);
        request.setPlatform("BITBUCKET");
        request.setRepoOwner("owner");
        request.setRepoName("repo");
        request.setTitle("PR Title");
        request.setDescription("PR Description");
        request.setSourceBranch("feature/branch");
        request.setTargetBranch("main");
        request.setReviewers(List.of("dev1"));
        request.setAccessToken("secret");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("BITBUCKET", request.getPlatform());
        assertEquals("owner", request.getRepoOwner());
        assertEquals("repo", request.getRepoName());
        assertEquals("PR Title", request.getTitle());
        assertEquals("PR Description", request.getDescription());
        assertEquals("feature/branch", request.getSourceBranch());
        assertEquals("main", request.getTargetBranch());
        assertEquals(List.of("dev1"), request.getReviewers());
        assertEquals("secret", request.getAccessToken());
    }

    @Test
    void should_allowNullOptionalFields() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertNull(request.getRepoOwner());
        assertNull(request.getRepoName());
        assertNull(request.getDescription());
        assertNull(request.getReviewers());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreatePullRequestRequest request1 = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .title("PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();
        CreatePullRequestRequest request2 = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .title("PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentTitle() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreatePullRequestRequest request1 = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .title("PR 1")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();
        CreatePullRequestRequest request2 = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .title("PR 2")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .title("Test PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("tenantId"));
        assertTrue(str.contains("taskId"));
        assertTrue(str.contains("platform"));
        assertTrue(str.contains("title"));
        assertTrue(str.contains("sourceBranch"));
        assertTrue(str.contains("targetBranch"));
    }

    @Test
    void should_handleEmptyReviewersList() {
        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .title("PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .reviewers(List.of())
                .build();

        assertNotNull(request.getReviewers());
        assertTrue(request.getReviewers().isEmpty());
    }
}
