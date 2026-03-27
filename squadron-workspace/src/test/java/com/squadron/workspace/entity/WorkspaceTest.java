package com.squadron.workspace.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    @Test
    void should_createWorkspace_withDefaultValues() {
        Workspace workspace = new Workspace();
        assertNull(workspace.getId());
        assertNull(workspace.getTenantId());
        assertNull(workspace.getContainerId());
        assertEquals("CREATING", workspace.getStatus());
    }

    @Test
    void should_buildWorkspace_withBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Workspace workspace = Workspace.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .providerType("KUBERNETES")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("ubuntu:22.04")
                .build();

        assertEquals(tenantId, workspace.getTenantId());
        assertEquals(taskId, workspace.getTaskId());
        assertEquals(userId, workspace.getUserId());
        assertEquals("KUBERNETES", workspace.getProviderType());
        assertEquals("READY", workspace.getStatus());
        assertEquals("https://github.com/test/repo.git", workspace.getRepoUrl());
        assertEquals("main", workspace.getBranch());
        assertEquals("ubuntu:22.04", workspace.getBaseImage());
    }

    @Test
    void should_setDefaultStatus_whenUsingBuilder() {
        Workspace workspace = Workspace.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .repoUrl("https://github.com/test/repo.git")
                .build();

        assertEquals("CREATING", workspace.getStatus());
    }

    @Test
    void should_setCreatedAt_onPrePersist() {
        Workspace workspace = new Workspace();
        assertNull(workspace.getCreatedAt());

        workspace.onCreate();

        assertNotNull(workspace.getCreatedAt());
    }

    @Test
    void should_notOverrideCreatedAt_onPrePersist_ifAlreadySet() {
        Instant existingTimestamp = Instant.parse("2025-01-01T00:00:00Z");
        Workspace workspace = new Workspace();
        workspace.setCreatedAt(existingTimestamp);

        workspace.onCreate();

        assertEquals(existingTimestamp, workspace.getCreatedAt());
    }

    @Test
    void should_setAndGetAllFields() {
        Workspace workspace = new Workspace();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        workspace.setId(id);
        workspace.setTenantId(tenantId);
        workspace.setTaskId(taskId);
        workspace.setUserId(userId);
        workspace.setProviderType("DOCKER");
        workspace.setContainerId("abc123");
        workspace.setStatus("ACTIVE");
        workspace.setRepoUrl("https://github.com/test/repo.git");
        workspace.setBranch("develop");
        workspace.setBaseImage("node:18");
        workspace.setResourceLimits("{\"memory\":\"512Mi\"}");
        workspace.setCreatedAt(now);
        workspace.setTerminatedAt(now);

        assertEquals(id, workspace.getId());
        assertEquals(tenantId, workspace.getTenantId());
        assertEquals(taskId, workspace.getTaskId());
        assertEquals(userId, workspace.getUserId());
        assertEquals("DOCKER", workspace.getProviderType());
        assertEquals("abc123", workspace.getContainerId());
        assertEquals("ACTIVE", workspace.getStatus());
        assertEquals("https://github.com/test/repo.git", workspace.getRepoUrl());
        assertEquals("develop", workspace.getBranch());
        assertEquals("node:18", workspace.getBaseImage());
        assertEquals("{\"memory\":\"512Mi\"}", workspace.getResourceLimits());
        assertEquals(now, workspace.getCreatedAt());
        assertEquals(now, workspace.getTerminatedAt());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        Workspace w1 = Workspace.builder().id(id).tenantId(UUID.randomUUID()).taskId(UUID.randomUUID())
                .userId(UUID.randomUUID()).providerType("KUBERNETES").repoUrl("https://github.com/test/repo.git").build();
        Workspace w2 = Workspace.builder().id(id).tenantId(w1.getTenantId()).taskId(w1.getTaskId())
                .userId(w1.getUserId()).providerType("KUBERNETES").repoUrl("https://github.com/test/repo.git").build();

        assertEquals(w1, w2);
        assertEquals(w1.hashCode(), w2.hashCode());
    }

    @Test
    void should_generateToString() {
        Workspace workspace = Workspace.builder()
                .providerType("KUBERNETES")
                .repoUrl("https://github.com/test/repo.git")
                .build();

        String str = workspace.toString();
        assertNotNull(str);
        assertTrue(str.contains("KUBERNETES"));
    }
}
