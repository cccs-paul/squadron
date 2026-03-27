package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateWorkspaceRequestTest {

    @Test
    void should_buildCreateWorkspaceRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> limits = Map.of("memory", "512Mi", "cpu", "1");

        CreateWorkspaceRequest request = CreateWorkspaceRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("ubuntu:22.04")
                .resourceLimits(limits)
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(taskId, request.getTaskId());
        assertEquals(userId, request.getUserId());
        assertEquals("https://github.com/test/repo.git", request.getRepoUrl());
        assertEquals("main", request.getBranch());
        assertEquals("ubuntu:22.04", request.getBaseImage());
        assertEquals(limits, request.getResourceLimits());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();
        assertNull(request.getTenantId());
        assertNull(request.getRepoUrl());
        assertNull(request.getResourceLimits());
    }

    @Test
    void should_setAndGetFields() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();
        UUID tenantId = UUID.randomUUID();
        request.setTenantId(tenantId);
        request.setRepoUrl("https://github.com/test/repo.git");

        assertEquals(tenantId, request.getTenantId());
        assertEquals("https://github.com/test/repo.git", request.getRepoUrl());
    }
}
