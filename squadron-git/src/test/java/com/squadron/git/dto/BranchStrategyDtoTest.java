package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BranchStrategyDtoTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        BranchStrategyDto dto = BranchStrategyDto.builder()
                .id(id)
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .createdAt(createdAt)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals("GITFLOW", dto.getStrategyType());
        assertEquals("squadron/", dto.getBranchPrefix());
        assertEquals("main", dto.getTargetBranch());
        assertEquals("develop", dto.getDevelopmentBranch());
        assertEquals("{prefix}{taskId}/{slug}", dto.getBranchNameTemplate());
        assertEquals(createdAt, dto.getCreatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        BranchStrategyDto dto = new BranchStrategyDto();
        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getProjectId());
        assertNull(dto.getStrategyType());
        assertNull(dto.getBranchPrefix());
        assertNull(dto.getTargetBranch());
        assertNull(dto.getDevelopmentBranch());
        assertNull(dto.getBranchNameTemplate());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        BranchStrategyDto dto = new BranchStrategyDto(
                id, tenantId, projectId, "TRUNK", "feat/", "main",
                "develop", "{prefix}{taskId}", createdAt);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals("TRUNK", dto.getStrategyType());
        assertEquals("feat/", dto.getBranchPrefix());
        assertEquals("main", dto.getTargetBranch());
        assertEquals("develop", dto.getDevelopmentBranch());
        assertEquals("{prefix}{taskId}", dto.getBranchNameTemplate());
        assertEquals(createdAt, dto.getCreatedAt());
    }

    @Test
    void should_supportSettersAndGetters() {
        BranchStrategyDto dto = new BranchStrategyDto();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        dto.setId(id);
        dto.setTenantId(tenantId);
        dto.setProjectId(projectId);
        dto.setStrategyType("GITHUB_FLOW");
        dto.setBranchPrefix("fix/");
        dto.setTargetBranch("main");
        dto.setDevelopmentBranch("develop");
        dto.setBranchNameTemplate("{prefix}{slug}");
        dto.setCreatedAt(createdAt);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(projectId, dto.getProjectId());
        assertEquals("GITHUB_FLOW", dto.getStrategyType());
        assertEquals("fix/", dto.getBranchPrefix());
        assertEquals("main", dto.getTargetBranch());
        assertEquals("develop", dto.getDevelopmentBranch());
        assertEquals("{prefix}{slug}", dto.getBranchNameTemplate());
        assertEquals(createdAt, dto.getCreatedAt());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");

        BranchStrategyDto dto1 = BranchStrategyDto.builder()
                .id(id)
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .targetBranch("main")
                .createdAt(createdAt)
                .build();
        BranchStrategyDto dto2 = BranchStrategyDto.builder()
                .id(id)
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .targetBranch("main")
                .createdAt(createdAt)
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentId() {
        BranchStrategyDto dto1 = BranchStrategyDto.builder()
                .id(UUID.randomUUID())
                .strategyType("GITFLOW")
                .build();
        BranchStrategyDto dto2 = BranchStrategyDto.builder()
                .id(UUID.randomUUID())
                .strategyType("GITFLOW")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_supportToString() {
        BranchStrategyDto dto = BranchStrategyDto.builder()
                .strategyType("GITFLOW")
                .targetBranch("main")
                .branchPrefix("squadron/")
                .build();

        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("strategyType"));
        assertTrue(str.contains("targetBranch"));
        assertTrue(str.contains("branchPrefix"));
    }
}
