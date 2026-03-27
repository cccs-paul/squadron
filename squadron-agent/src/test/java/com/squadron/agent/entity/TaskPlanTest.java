package com.squadron.agent.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskPlanTest {

    @Test
    void should_buildTaskPlan_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Instant approvedAt = Instant.now();

        TaskPlan plan = TaskPlan.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(2)
                .planContent("Step 1: Implement feature")
                .status("APPROVED")
                .approvedBy(approvedBy)
                .approvedAt(approvedAt)
                .build();

        assertEquals(id, plan.getId());
        assertEquals(tenantId, plan.getTenantId());
        assertEquals(taskId, plan.getTaskId());
        assertEquals(conversationId, plan.getConversationId());
        assertEquals(2, plan.getVersion());
        assertEquals("Step 1: Implement feature", plan.getPlanContent());
        assertEquals("APPROVED", plan.getStatus());
        assertEquals(approvedBy, plan.getApprovedBy());
        assertEquals(approvedAt, plan.getApprovedAt());
    }

    @Test
    void should_haveDefaultValues_when_usingBuilder() {
        TaskPlan plan = TaskPlan.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .planContent("Some plan")
                .build();

        assertEquals(1, plan.getVersion());
        assertEquals("DRAFT", plan.getStatus());
    }

    @Test
    void should_setTimestamp_when_onCreateCalled() {
        TaskPlan plan = TaskPlan.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .planContent("Plan content")
                .build();

        plan.onCreate();

        assertNotNull(plan.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        TaskPlan plan = new TaskPlan();
        UUID tenantId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Instant now = Instant.now();

        plan.setTenantId(tenantId);
        plan.setVersion(3);
        plan.setPlanContent("Updated plan");
        plan.setStatus("APPROVED");
        plan.setApprovedBy(approvedBy);
        plan.setApprovedAt(now);

        assertEquals(tenantId, plan.getTenantId());
        assertEquals(3, plan.getVersion());
        assertEquals("Updated plan", plan.getPlanContent());
        assertEquals("APPROVED", plan.getStatus());
        assertEquals(approvedBy, plan.getApprovedBy());
        assertEquals(now, plan.getApprovedAt());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TaskPlan p1 = TaskPlan.builder().id(id).tenantId(tenantId).planContent("plan").version(1).status("DRAFT").build();
        TaskPlan p2 = TaskPlan.builder().id(id).tenantId(tenantId).planContent("plan").version(1).status("DRAFT").build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_handleNullApprovalFields_when_draftPlan() {
        TaskPlan plan = TaskPlan.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .planContent("Draft plan")
                .build();

        assertNull(plan.getApprovedBy());
        assertNull(plan.getApprovedAt());
    }
}
