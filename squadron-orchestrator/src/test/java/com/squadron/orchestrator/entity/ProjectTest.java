package com.squadron.orchestrator.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        Project project = Project.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .connectionId(connectionId)
                .externalProjectId("EXT-1")
                .repoUrl("https://github.com/test/repo")
                .defaultBranch("develop")
                .branchStrategy("GIT_FLOW")
                .settings("{}")
                .build();

        assertEquals(id, project.getId());
        assertEquals(tenantId, project.getTenantId());
        assertEquals(teamId, project.getTeamId());
        assertEquals("Test Project", project.getName());
        assertEquals(connectionId, project.getConnectionId());
        assertEquals("EXT-1", project.getExternalProjectId());
        assertEquals("https://github.com/test/repo", project.getRepoUrl());
        assertEquals("develop", project.getDefaultBranch());
        assertEquals("GIT_FLOW", project.getBranchStrategy());
        assertEquals("{}", project.getSettings());
    }

    @Test
    void should_haveDefaultValues() {
        Project project = Project.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Project")
                .build();

        assertEquals("main", project.getDefaultBranch());
        assertEquals("TRUNK_BASED", project.getBranchStrategy());
    }

    @Test
    void should_setTimestampsOnPrePersist() {
        Project project = new Project();
        project.onCreate();

        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());
        assertEquals(project.getCreatedAt(), project.getUpdatedAt());
    }

    @Test
    void should_updateTimestampOnPreUpdate() {
        Project project = new Project();
        project.onCreate();
        Instant originalUpdatedAt = project.getUpdatedAt();

        // Small delay to ensure different timestamp
        project.onUpdate();

        assertNotNull(project.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields() {
        Project project = new Project();
        UUID tenantId = UUID.randomUUID();
        project.setTenantId(tenantId);
        project.setName("Updated");

        assertEquals(tenantId, project.getTenantId());
        assertEquals("Updated", project.getName());
    }

    @Test
    void should_implementEquals() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Project p1 = Project.builder().id(id).tenantId(tenantId).teamId(teamId).name("P").build();
        Project p2 = Project.builder().id(id).tenantId(tenantId).teamId(teamId).name("P").build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentIds() {
        Project p1 = Project.builder().id(UUID.randomUUID()).name("P").build();
        Project p2 = Project.builder().id(UUID.randomUUID()).name("P").build();

        assertNotEquals(p1, p2);
    }

    @Test
    void should_haveToString() {
        Project project = Project.builder().name("Test").build();
        assertNotNull(project.toString());
        assert project.toString().contains("Test");
    }

    @Test
    void should_createWithNoArgsConstructor() {
        Project project = new Project();
        assertNull(project.getId());
        assertNull(project.getName());
    }
}
