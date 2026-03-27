package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDtoTest {

    @Test
    void should_buildWorkspaceDto() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> limits = Map.of("memory", "1Gi");

        WorkspaceDto dto = WorkspaceDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .providerType("KUBERNETES")
                .containerId("pod-123")
                .status("READY")
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("ubuntu:22.04")
                .resourceLimits(limits)
                .createdAt(now)
                .terminatedAt(null)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(userId, dto.getUserId());
        assertEquals("KUBERNETES", dto.getProviderType());
        assertEquals("pod-123", dto.getContainerId());
        assertEquals("READY", dto.getStatus());
        assertEquals("https://github.com/test/repo.git", dto.getRepoUrl());
        assertEquals("main", dto.getBranch());
        assertEquals("ubuntu:22.04", dto.getBaseImage());
        assertEquals(limits, dto.getResourceLimits());
        assertEquals(now, dto.getCreatedAt());
        assertNull(dto.getTerminatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        WorkspaceDto dto = new WorkspaceDto();
        assertNull(dto.getId());
        assertNull(dto.getStatus());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        WorkspaceDto dto1 = WorkspaceDto.builder().id(id).status("READY").build();
        WorkspaceDto dto2 = WorkspaceDto.builder().id(id).status("READY").build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}
