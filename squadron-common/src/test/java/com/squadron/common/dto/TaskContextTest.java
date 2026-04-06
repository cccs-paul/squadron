package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskContextTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskContext context = TaskContext.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .projectId(projectId)
                .userId(userId)
                .connectionId(connectionId)
                .repoUrl("https://github.com/org/repo.git")
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .branchNamingTemplate("{prefix}{taskId}/{slug}")
                .externalId("JIRA-123")
                .title("Fix authentication bug")
                .build();

        assertEquals(taskId, context.getTaskId());
        assertEquals(tenantId, context.getTenantId());
        assertEquals(projectId, context.getProjectId());
        assertEquals(userId, context.getUserId());
        assertEquals(connectionId, context.getConnectionId());
        assertEquals("https://github.com/org/repo.git", context.getRepoUrl());
        assertEquals("main", context.getDefaultBranch());
        assertEquals("TRUNK_BASED", context.getBranchStrategy());
        assertEquals("{prefix}{taskId}/{slug}", context.getBranchNamingTemplate());
        assertEquals("JIRA-123", context.getExternalId());
        assertEquals("Fix authentication bug", context.getTitle());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        TaskContext context = new TaskContext();

        assertNull(context.getTaskId());
        assertNull(context.getTenantId());
        assertNull(context.getProjectId());
        assertNull(context.getUserId());
        assertNull(context.getConnectionId());
        assertNull(context.getRepoUrl());
        assertNull(context.getDefaultBranch());
        assertNull(context.getBranchStrategy());
        assertNull(context.getBranchNamingTemplate());
        assertNull(context.getExternalId());
        assertNull(context.getTitle());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        TaskContext context = new TaskContext(
                taskId, tenantId, projectId, userId, connectionId,
                "https://gitlab.com/org/repo.git", "develop",
                "GIT_FLOW", "{type}/{taskId}-{slug}",
                "GH-456", "Add search feature"
        );

        assertEquals(taskId, context.getTaskId());
        assertEquals(tenantId, context.getTenantId());
        assertEquals(projectId, context.getProjectId());
        assertEquals(userId, context.getUserId());
        assertEquals(connectionId, context.getConnectionId());
        assertEquals("https://gitlab.com/org/repo.git", context.getRepoUrl());
        assertEquals("develop", context.getDefaultBranch());
        assertEquals("GIT_FLOW", context.getBranchStrategy());
        assertEquals("{type}/{taskId}-{slug}", context.getBranchNamingTemplate());
        assertEquals("GH-456", context.getExternalId());
        assertEquals("Add search feature", context.getTitle());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        TaskContext context = new TaskContext();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        context.setTaskId(taskId);
        context.setTenantId(tenantId);
        context.setRepoUrl("https://github.com/org/repo.git");
        context.setDefaultBranch("main");
        context.setTitle("Updated task");

        assertEquals(taskId, context.getTaskId());
        assertEquals(tenantId, context.getTenantId());
        assertEquals("https://github.com/org/repo.git", context.getRepoUrl());
        assertEquals("main", context.getDefaultBranch());
        assertEquals("Updated task", context.getTitle());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskContext context1 = TaskContext.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .title("Task A")
                .build();

        TaskContext context2 = TaskContext.builder()
                .taskId(taskId)
                .tenantId(tenantId)
                .title("Task A")
                .build();

        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TaskContext context1 = TaskContext.builder()
                .title("Task A")
                .repoUrl("https://github.com/org/repo-a.git")
                .build();

        TaskContext context2 = TaskContext.builder()
                .title("Task B")
                .repoUrl("https://github.com/org/repo-b.git")
                .build();

        assertNotEquals(context1, context2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        TaskContext context = TaskContext.builder()
                .repoUrl("https://github.com/org/repo.git")
                .defaultBranch("main")
                .externalId("JIRA-999")
                .title("Fix login")
                .build();

        String str = context.toString();
        assertTrue(str.contains("https://github.com/org/repo.git"));
        assertTrue(str.contains("main"));
        assertTrue(str.contains("JIRA-999"));
        assertTrue(str.contains("Fix login"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        TaskContext context = TaskContext.builder()
                .taskId(null)
                .tenantId(null)
                .repoUrl(null)
                .title(null)
                .build();

        assertNull(context.getTaskId());
        assertNull(context.getTenantId());
        assertNull(context.getRepoUrl());
        assertNull(context.getTitle());
    }
}
