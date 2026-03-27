package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSpecTest {

    @Test
    void should_buildWorkspaceSpec() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> limits = Map.of("memory", "512Mi");

        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("ubuntu:22.04")
                .resourceLimits(limits)
                .providerType("KUBERNETES")
                .build();

        assertEquals(tenantId, spec.getTenantId());
        assertEquals(taskId, spec.getTaskId());
        assertEquals(userId, spec.getUserId());
        assertEquals("https://github.com/test/repo.git", spec.getRepoUrl());
        assertEquals("main", spec.getBranch());
        assertEquals("ubuntu:22.04", spec.getBaseImage());
        assertEquals(limits, spec.getResourceLimits());
        assertEquals("KUBERNETES", spec.getProviderType());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        WorkspaceSpec spec = new WorkspaceSpec();
        assertNull(spec.getTenantId());
        assertNull(spec.getProviderType());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        WorkspaceSpec s1 = WorkspaceSpec.builder().tenantId(tenantId).providerType("DOCKER").build();
        WorkspaceSpec s2 = WorkspaceSpec.builder().tenantId(tenantId).providerType("DOCKER").build();

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
