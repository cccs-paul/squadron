package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateBranchStrategyRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .branchPrefix("squadron/")
                .targetBranch("main")
                .developmentBranch("develop")
                .branchNameTemplate("{prefix}{taskId}/{slug}")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("GITFLOW", request.getStrategyType());
        assertEquals("squadron/", request.getBranchPrefix());
        assertEquals("main", request.getTargetBranch());
        assertEquals("develop", request.getDevelopmentBranch());
        assertEquals("{prefix}{taskId}/{slug}", request.getBranchNameTemplate());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreateBranchStrategyRequest request = new CreateBranchStrategyRequest();
        assertNull(request.getTenantId());
        assertNull(request.getProjectId());
        assertNull(request.getStrategyType());
        assertNull(request.getBranchPrefix());
        assertNull(request.getTargetBranch());
        assertNull(request.getDevelopmentBranch());
        assertNull(request.getBranchNameTemplate());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CreateBranchStrategyRequest request = new CreateBranchStrategyRequest(
                tenantId, projectId, "TRUNK", "feat/", "main", null, "{prefix}{slug}");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("TRUNK", request.getStrategyType());
        assertEquals("feat/", request.getBranchPrefix());
        assertEquals("main", request.getTargetBranch());
        assertNull(request.getDevelopmentBranch());
        assertEquals("{prefix}{slug}", request.getBranchNameTemplate());
    }

    @Test
    void should_supportSettersAndGetters() {
        CreateBranchStrategyRequest request = new CreateBranchStrategyRequest();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        request.setTenantId(tenantId);
        request.setProjectId(projectId);
        request.setStrategyType("GITHUB_FLOW");
        request.setBranchPrefix("fix/");
        request.setTargetBranch("main");
        request.setDevelopmentBranch("develop");
        request.setBranchNameTemplate("{prefix}{taskId}");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(projectId, request.getProjectId());
        assertEquals("GITHUB_FLOW", request.getStrategyType());
        assertEquals("fix/", request.getBranchPrefix());
        assertEquals("main", request.getTargetBranch());
        assertEquals("develop", request.getDevelopmentBranch());
        assertEquals("{prefix}{taskId}", request.getBranchNameTemplate());
    }

    @Test
    void should_allowNullOptionalFields() {
        UUID tenantId = UUID.randomUUID();
        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("TRUNK")
                .targetBranch("main")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertNull(request.getProjectId());
        assertEquals("TRUNK", request.getStrategyType());
        assertNull(request.getBranchPrefix());
        assertEquals("main", request.getTargetBranch());
        assertNull(request.getDevelopmentBranch());
        assertNull(request.getBranchNameTemplate());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CreateBranchStrategyRequest request1 = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .targetBranch("main")
                .build();
        CreateBranchStrategyRequest request2 = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType("GITFLOW")
                .targetBranch("main")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentStrategyType() {
        UUID tenantId = UUID.randomUUID();
        CreateBranchStrategyRequest request1 = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("GITFLOW")
                .targetBranch("main")
                .build();
        CreateBranchStrategyRequest request2 = CreateBranchStrategyRequest.builder()
                .tenantId(tenantId)
                .strategyType("TRUNK")
                .targetBranch("main")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        CreateBranchStrategyRequest request = CreateBranchStrategyRequest.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("GITFLOW")
                .targetBranch("main")
                .branchPrefix("squadron/")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("tenantId"));
        assertTrue(str.contains("strategyType"));
        assertTrue(str.contains("targetBranch"));
        assertTrue(str.contains("branchPrefix"));
    }
}
