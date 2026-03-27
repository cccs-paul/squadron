package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateProjectRequestTest {

    @Test
    void should_createWithBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .defaultBranch("develop")
                .branchStrategy("GIT_FLOW")
                .connectionId(connectionId)
                .externalProjectId("EXT-123")
                .settings("{\"key\":\"value\"}")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals("Test Project", request.getName());
        assertEquals("https://github.com/test/repo", request.getRepoUrl());
        assertEquals("develop", request.getDefaultBranch());
        assertEquals("GIT_FLOW", request.getBranchStrategy());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("EXT-123", request.getExternalProjectId());
        assertEquals("{\"key\":\"value\"}", request.getSettings());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreateProjectRequest request = new CreateProjectRequest();
        assertNull(request.getTenantId());
        assertNull(request.getName());
    }

    @Test
    void should_setAndGetFields() {
        CreateProjectRequest request = new CreateProjectRequest();
        UUID tenantId = UUID.randomUUID();
        request.setTenantId(tenantId);
        request.setName("Updated");

        assertEquals(tenantId, request.getTenantId());
        assertEquals("Updated", request.getName());
    }

    @Test
    void should_implementEquals() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateProjectRequest r1 = CreateProjectRequest.builder()
                .tenantId(tenantId).teamId(teamId).name("Test").build();
        CreateProjectRequest r2 = CreateProjectRequest.builder()
                .tenantId(tenantId).teamId(teamId).name("Test").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        CreateProjectRequest r1 = CreateProjectRequest.builder().name("A").build();
        CreateProjectRequest r2 = CreateProjectRequest.builder().name("B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        CreateProjectRequest request = CreateProjectRequest.builder().name("Test").build();
        assertNotNull(request.toString());
        assert request.toString().contains("Test");
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateProjectRequest request = new CreateProjectRequest(
                tenantId, teamId, "Name", "url", "main", "TRUNK_BASED",
                connectionId, "ext-1", "{}"
        );

        assertEquals(tenantId, request.getTenantId());
        assertEquals("Name", request.getName());
    }
}
