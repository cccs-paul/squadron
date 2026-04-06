package com.squadron.agent.dto;

import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceInfoTest {

    @Test
    void should_buildWithAllFields() {
        UUID workspaceId = UUID.randomUUID();
        String branchName = "feature/TASK-42";
        String repoUrl = "https://github.com/acme/repo.git";
        GitAuthMode gitAuthMode = GitAuthMode.HTTPS_TOKEN;
        String containerId = "container-abc-123";

        WorkspaceInfo info = WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .repoUrl(repoUrl)
                .gitAuthMode(gitAuthMode)
                .containerId(containerId)
                .build();

        assertEquals(workspaceId, info.getWorkspaceId());
        assertEquals(branchName, info.getBranchName());
        assertEquals(repoUrl, info.getRepoUrl());
        assertEquals(gitAuthMode, info.getGitAuthMode());
        assertEquals(containerId, info.getContainerId());
    }

    @Test
    void should_buildWithMinimalFields() {
        UUID workspaceId = UUID.randomUUID();
        String branchName = "fix/TASK-99";

        WorkspaceInfo info = WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .build();

        assertEquals(workspaceId, info.getWorkspaceId());
        assertEquals(branchName, info.getBranchName());
        assertNull(info.getRepoUrl());
        assertNull(info.getGitAuthMode());
        assertNull(info.getContainerId());
    }

    @Test
    void should_implementEquality() {
        UUID workspaceId = UUID.randomUUID();
        String branchName = "feature/TASK-7";
        String repoUrl = "git@github.com:acme/repo.git";
        GitAuthMode gitAuthMode = GitAuthMode.SSH_KEY;
        String containerId = "ctr-xyz";

        WorkspaceInfo a = WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .repoUrl(repoUrl)
                .gitAuthMode(gitAuthMode)
                .containerId(containerId)
                .build();

        WorkspaceInfo b = WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .repoUrl(repoUrl)
                .gitAuthMode(gitAuthMode)
                .containerId(containerId)
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Verify inequality when a field differs
        WorkspaceInfo c = WorkspaceInfo.builder()
                .workspaceId(UUID.randomUUID())
                .branchName(branchName)
                .repoUrl(repoUrl)
                .gitAuthMode(gitAuthMode)
                .containerId(containerId)
                .build();

        assertNotEquals(a, c);
    }

    @Test
    void should_implementToString() {
        UUID workspaceId = UUID.randomUUID();
        String branchName = "feature/TASK-11";
        String repoUrl = "https://github.com/acme/repo.git";

        WorkspaceInfo info = WorkspaceInfo.builder()
                .workspaceId(workspaceId)
                .branchName(branchName)
                .repoUrl(repoUrl)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .containerId("ctr-001")
                .build();

        String str = info.toString();
        assertTrue(str.contains(workspaceId.toString()), "toString should contain workspaceId");
        assertTrue(str.contains(branchName), "toString should contain branchName");
        assertTrue(str.contains(repoUrl), "toString should contain repoUrl");
        assertTrue(str.contains("HTTPS_TOKEN"), "toString should contain gitAuthMode");
        assertTrue(str.contains("ctr-001"), "toString should contain containerId");
    }

    @Test
    void should_supportNoArgsConstructor() {
        WorkspaceInfo info = new WorkspaceInfo();

        assertNull(info.getWorkspaceId());
        assertNull(info.getBranchName());
        assertNull(info.getRepoUrl());
        assertNull(info.getGitAuthMode());
        assertNull(info.getContainerId());

        UUID workspaceId = UUID.randomUUID();
        info.setWorkspaceId(workspaceId);
        info.setBranchName("hotfix/TASK-3");
        info.setRepoUrl("https://gitlab.com/acme/repo.git");
        info.setGitAuthMode(GitAuthMode.SSH_KEY);
        info.setContainerId("ctr-999");

        assertEquals(workspaceId, info.getWorkspaceId());
        assertEquals("hotfix/TASK-3", info.getBranchName());
        assertEquals("https://gitlab.com/acme/repo.git", info.getRepoUrl());
        assertEquals(GitAuthMode.SSH_KEY, info.getGitAuthMode());
        assertEquals("ctr-999", info.getContainerId());
    }
}
