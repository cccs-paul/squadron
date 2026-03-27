package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskSyncRequestTest {

    @Test
    void should_createWithBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskSyncRequest request = TaskSyncRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .platformConnectionId(connectionId)
                .projectKey("PROJ")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals(projectId, request.getProjectId());
        assertEquals(connectionId, request.getPlatformConnectionId());
        assertEquals("PROJ", request.getProjectKey());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskSyncRequest request = new TaskSyncRequest();
        assertNull(request.getTenantId());
        assertNull(request.getTeamId());
        assertNull(request.getProjectId());
        assertNull(request.getPlatformConnectionId());
        assertNull(request.getProjectKey());
    }

    @Test
    void should_setAndGetFields() {
        TaskSyncRequest request = new TaskSyncRequest();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        request.setTenantId(tenantId);
        request.setTeamId(teamId);
        request.setProjectId(projectId);
        request.setPlatformConnectionId(connectionId);
        request.setProjectKey("KEY-1");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals(projectId, request.getProjectId());
        assertEquals(connectionId, request.getPlatformConnectionId());
        assertEquals("KEY-1", request.getProjectKey());
    }

    @Test
    void should_implementEquals() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskSyncRequest r1 = TaskSyncRequest.builder()
                .tenantId(tenantId).teamId(teamId).projectId(projectId)
                .platformConnectionId(connectionId).projectKey("K").build();
        TaskSyncRequest r2 = TaskSyncRequest.builder()
                .tenantId(tenantId).teamId(teamId).projectId(projectId)
                .platformConnectionId(connectionId).projectKey("K").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        TaskSyncRequest r1 = TaskSyncRequest.builder().projectKey("A").build();
        TaskSyncRequest r2 = TaskSyncRequest.builder().projectKey("B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        TaskSyncRequest request = TaskSyncRequest.builder().projectKey("PROJ-X").build();
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("PROJ-X"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskSyncRequest request = new TaskSyncRequest(
                tenantId, teamId, projectId, connectionId, "MY-KEY"
        );

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals(projectId, request.getProjectId());
        assertEquals(connectionId, request.getPlatformConnectionId());
        assertEquals("MY-KEY", request.getProjectKey());
    }
}
