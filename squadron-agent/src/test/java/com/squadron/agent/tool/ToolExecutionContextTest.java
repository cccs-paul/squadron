package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionContextTest {

    @Test
    void should_buildContext_when_usingBuilder() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> params = Map.of("filePath", "/src/Main.java");

        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(taskId)
                .tenantId(tenantId)
                .parameters(params)
                .accessToken("oauth-token-123")
                .build();

        assertEquals(workspaceId, ctx.getWorkspaceId());
        assertEquals(taskId, ctx.getTaskId());
        assertEquals(tenantId, ctx.getTenantId());
        assertEquals(params, ctx.getParameters());
        assertEquals("oauth-token-123", ctx.getAccessToken());
    }

    @Test
    void should_createContext_when_usingNoArgsConstructor() {
        ToolExecutionContext ctx = new ToolExecutionContext();

        assertNull(ctx.getWorkspaceId());
        assertNull(ctx.getTaskId());
        assertNull(ctx.getTenantId());
        assertNull(ctx.getParameters());
        assertNull(ctx.getAccessToken());
    }

    @Test
    void should_createContext_when_usingAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> params = Map.of("command", "ls -la");

        ToolExecutionContext ctx = new ToolExecutionContext(workspaceId, taskId, tenantId, params, "token");

        assertEquals(workspaceId, ctx.getWorkspaceId());
        assertEquals(taskId, ctx.getTaskId());
        assertEquals(tenantId, ctx.getTenantId());
        assertEquals(params, ctx.getParameters());
        assertEquals("token", ctx.getAccessToken());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ToolExecutionContext ctx = new ToolExecutionContext();
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ctx.setWorkspaceId(workspaceId);
        ctx.setTaskId(taskId);
        ctx.setTenantId(tenantId);
        ctx.setParameters(Map.of("key", "value"));
        ctx.setAccessToken("my-token");

        assertEquals(workspaceId, ctx.getWorkspaceId());
        assertEquals(taskId, ctx.getTaskId());
        assertEquals(tenantId, ctx.getTenantId());
        assertEquals(Map.of("key", "value"), ctx.getParameters());
        assertEquals("my-token", ctx.getAccessToken());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ToolExecutionContext c1 = ToolExecutionContext.builder()
                .workspaceId(workspaceId).taskId(taskId).tenantId(tenantId).build();
        ToolExecutionContext c2 = ToolExecutionContext.builder()
                .workspaceId(workspaceId).taskId(taskId).tenantId(tenantId).build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ToolExecutionContext c1 = ToolExecutionContext.builder().taskId(UUID.randomUUID()).build();
        ToolExecutionContext c2 = ToolExecutionContext.builder().taskId(UUID.randomUUID()).build();

        assertNotEquals(c1, c2);
    }

    @Test
    void should_haveToString_when_called() {
        UUID taskId = UUID.randomUUID();
        ToolExecutionContext ctx = ToolExecutionContext.builder().taskId(taskId).build();
        String toString = ctx.toString();

        assertNotNull(toString);
        assertTrue(toString.contains(taskId.toString()));
    }

    @Test
    void should_allowNullAccessToken_when_notNeeded() {
        ToolExecutionContext ctx = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .accessToken(null)
                .build();

        assertNull(ctx.getAccessToken());
    }
}
