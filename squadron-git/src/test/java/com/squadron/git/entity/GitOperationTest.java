package com.squadron.git.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GitOperationTest {

    @Test
    void should_createGitOperation_withBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        GitOperation op = GitOperation.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .workspaceId(workspaceId)
                .operationType("CLONE")
                .status("PENDING")
                .details("{\"branch\": \"main\"}")
                .build();

        assertEquals(id, op.getId());
        assertEquals(tenantId, op.getTenantId());
        assertEquals(taskId, op.getTaskId());
        assertEquals(workspaceId, op.getWorkspaceId());
        assertEquals("CLONE", op.getOperationType());
        assertEquals("PENDING", op.getStatus());
        assertEquals("{\"branch\": \"main\"}", op.getDetails());
        assertNull(op.getErrorMessage());
        assertNull(op.getCreatedAt());
        assertNull(op.getCompletedAt());
    }

    @Test
    void should_haveDefaultStatus_whenUsingBuilder() {
        GitOperation op = GitOperation.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .workspaceId(UUID.randomUUID())
                .operationType("PUSH")
                .build();

        assertEquals("PENDING", op.getStatus());
    }

    @Test
    void should_setErrorMessage() {
        GitOperation op = new GitOperation();
        op.setErrorMessage("Something went wrong");
        assertEquals("Something went wrong", op.getErrorMessage());
    }

    @Test
    void should_setCompletedAt() {
        GitOperation op = new GitOperation();
        Instant now = Instant.now();
        op.setCompletedAt(now);
        assertEquals(now, op.getCompletedAt());
    }

    @Test
    void should_setAndGetAllFields() {
        GitOperation op = new GitOperation();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.now();

        op.setId(id);
        op.setTenantId(tenantId);
        op.setTaskId(taskId);
        op.setWorkspaceId(workspaceId);
        op.setOperationType("COMMIT");
        op.setStatus("COMPLETED");
        op.setDetails("{\"message\": \"fix bug\"}");
        op.setErrorMessage(null);
        op.setCreatedAt(now);
        op.setCompletedAt(now);

        assertEquals(id, op.getId());
        assertEquals(tenantId, op.getTenantId());
        assertEquals(taskId, op.getTaskId());
        assertEquals(workspaceId, op.getWorkspaceId());
        assertEquals("COMMIT", op.getOperationType());
        assertEquals("COMPLETED", op.getStatus());
        assertEquals("{\"message\": \"fix bug\"}", op.getDetails());
        assertNull(op.getErrorMessage());
        assertEquals(now, op.getCreatedAt());
        assertEquals(now, op.getCompletedAt());
    }

    @Test
    void should_supportAllOperationTypes() {
        for (String type : new String[]{"CLONE", "CHECKOUT", "BRANCH", "COMMIT", "PUSH", "MERGE", "CREATE_PR"}) {
            GitOperation op = GitOperation.builder()
                    .tenantId(UUID.randomUUID())
                    .taskId(UUID.randomUUID())
                    .workspaceId(UUID.randomUUID())
                    .operationType(type)
                    .build();
            assertEquals(type, op.getOperationType());
        }
    }
}
