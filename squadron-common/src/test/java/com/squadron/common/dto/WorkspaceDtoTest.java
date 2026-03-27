package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> resourceLimits = Map.of("cpu", "2", "memory", "4Gi");
        Instant now = Instant.now();

        WorkspaceDto dto = WorkspaceDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .providerType("KUBERNETES")
                .containerId("pod-abc123")
                .status("RUNNING")
                .repoUrl("https://github.com/org/repo")
                .branch("feature/fix-auth")
                .baseImage("ubuntu:22.04")
                .resourceLimits(resourceLimits)
                .createdAt(now)
                .terminatedAt(null)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(userId, dto.getUserId());
        assertEquals("KUBERNETES", dto.getProviderType());
        assertEquals("pod-abc123", dto.getContainerId());
        assertEquals("RUNNING", dto.getStatus());
        assertEquals("https://github.com/org/repo", dto.getRepoUrl());
        assertEquals("feature/fix-auth", dto.getBranch());
        assertEquals("ubuntu:22.04", dto.getBaseImage());
        assertEquals(resourceLimits, dto.getResourceLimits());
        assertEquals(now, dto.getCreatedAt());
        assertNull(dto.getTerminatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        WorkspaceDto dto = new WorkspaceDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getUserId());
        assertNull(dto.getProviderType());
        assertNull(dto.getContainerId());
        assertNull(dto.getStatus());
        assertNull(dto.getRepoUrl());
        assertNull(dto.getBranch());
        assertNull(dto.getBaseImage());
        assertNull(dto.getResourceLimits());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getTerminatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> resourceLimits = Map.of("cpu", "1", "memory", "2Gi");
        Instant created = Instant.now();
        Instant terminated = Instant.now();

        WorkspaceDto dto = new WorkspaceDto(
                id, tenantId, taskId, userId, "DOCKER", "container-xyz",
                "TERMINATED", "https://gitlab.com/org/repo", "main",
                "node:18", resourceLimits, created, terminated
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(userId, dto.getUserId());
        assertEquals("DOCKER", dto.getProviderType());
        assertEquals("container-xyz", dto.getContainerId());
        assertEquals("TERMINATED", dto.getStatus());
        assertEquals("https://gitlab.com/org/repo", dto.getRepoUrl());
        assertEquals("main", dto.getBranch());
        assertEquals("node:18", dto.getBaseImage());
        assertEquals(resourceLimits, dto.getResourceLimits());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(terminated, dto.getTerminatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        WorkspaceDto dto = new WorkspaceDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setProviderType("KUBERNETES");
        dto.setContainerId("pod-new");
        dto.setStatus("PROVISIONING");
        dto.setBranch("develop");
        dto.setBaseImage("python:3.11");

        assertEquals(id, dto.getId());
        assertEquals("KUBERNETES", dto.getProviderType());
        assertEquals("pod-new", dto.getContainerId());
        assertEquals("PROVISIONING", dto.getStatus());
        assertEquals("develop", dto.getBranch());
        assertEquals("python:3.11", dto.getBaseImage());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        WorkspaceDto dto1 = WorkspaceDto.builder()
                .id(id)
                .tenantId(tenantId)
                .providerType("DOCKER")
                .status("RUNNING")
                .build();

        WorkspaceDto dto2 = WorkspaceDto.builder()
                .id(id)
                .tenantId(tenantId)
                .providerType("DOCKER")
                .status("RUNNING")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        WorkspaceDto dto1 = WorkspaceDto.builder()
                .status("RUNNING")
                .build();

        WorkspaceDto dto2 = WorkspaceDto.builder()
                .status("TERMINATED")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        WorkspaceDto dto = WorkspaceDto.builder()
                .providerType("KUBERNETES")
                .containerId("pod-abc")
                .status("RUNNING")
                .branch("feature/test")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("KUBERNETES"));
        assertTrue(str.contains("pod-abc"));
        assertTrue(str.contains("RUNNING"));
        assertTrue(str.contains("feature/test"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        WorkspaceDto dto = WorkspaceDto.builder()
                .id(null)
                .tenantId(null)
                .taskId(null)
                .userId(null)
                .providerType(null)
                .containerId(null)
                .status(null)
                .repoUrl(null)
                .branch(null)
                .baseImage(null)
                .resourceLimits(null)
                .createdAt(null)
                .terminatedAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getUserId());
        assertNull(dto.getProviderType());
        assertNull(dto.getContainerId());
        assertNull(dto.getStatus());
        assertNull(dto.getRepoUrl());
        assertNull(dto.getBranch());
        assertNull(dto.getBaseImage());
        assertNull(dto.getResourceLimits());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getTerminatedAt());
    }

    @Test
    void should_handleEmptyResourceLimits_when_emptyMapProvided() {
        WorkspaceDto dto = WorkspaceDto.builder()
                .resourceLimits(Map.of())
                .build();

        assertNotNull(dto.getResourceLimits());
        assertTrue(dto.getResourceLimits().isEmpty());
    }

    @Test
    void should_handleActiveWorkspace_when_notTerminated() {
        Instant created = Instant.now();
        WorkspaceDto dto = WorkspaceDto.builder()
                .status("RUNNING")
                .createdAt(created)
                .terminatedAt(null)
                .build();

        assertEquals("RUNNING", dto.getStatus());
        assertNotNull(dto.getCreatedAt());
        assertNull(dto.getTerminatedAt());
    }
}
