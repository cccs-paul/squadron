package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskPlanDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Instant now = Instant.now();

        TaskPlanDto dto = TaskPlanDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(3)
                .planContent("1. Analyze codebase\n2. Implement fix\n3. Write tests")
                .status("APPROVED")
                .approvedBy(approvedBy)
                .approvedAt(now)
                .createdAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(conversationId, dto.getConversationId());
        assertEquals(3, dto.getVersion());
        assertEquals("1. Analyze codebase\n2. Implement fix\n3. Write tests", dto.getPlanContent());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals(approvedBy, dto.getApprovedBy());
        assertEquals(now, dto.getApprovedAt());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        TaskPlanDto dto = new TaskPlanDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getConversationId());
        assertNull(dto.getVersion());
        assertNull(dto.getPlanContent());
        assertNull(dto.getStatus());
        assertNull(dto.getApprovedBy());
        assertNull(dto.getApprovedAt());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        Instant approvedAt = Instant.now();
        Instant createdAt = Instant.now();

        TaskPlanDto dto = new TaskPlanDto(
                id, tenantId, taskId, conversationId, 1,
                "Plan content here", "DRAFT", approvedBy, approvedAt, createdAt
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(conversationId, dto.getConversationId());
        assertEquals(1, dto.getVersion());
        assertEquals("Plan content here", dto.getPlanContent());
        assertEquals("DRAFT", dto.getStatus());
        assertEquals(approvedBy, dto.getApprovedBy());
        assertEquals(approvedAt, dto.getApprovedAt());
        assertEquals(createdAt, dto.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        TaskPlanDto dto = new TaskPlanDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setVersion(2);
        dto.setPlanContent("Updated plan");
        dto.setStatus("PENDING_APPROVAL");

        assertEquals(id, dto.getId());
        assertEquals(2, dto.getVersion());
        assertEquals("Updated plan", dto.getPlanContent());
        assertEquals("PENDING_APPROVAL", dto.getStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskPlanDto dto1 = TaskPlanDto.builder()
                .id(id)
                .tenantId(tenantId)
                .version(1)
                .status("DRAFT")
                .build();

        TaskPlanDto dto2 = TaskPlanDto.builder()
                .id(id)
                .tenantId(tenantId)
                .version(1)
                .status("DRAFT")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TaskPlanDto dto1 = TaskPlanDto.builder()
                .version(1)
                .status("DRAFT")
                .build();

        TaskPlanDto dto2 = TaskPlanDto.builder()
                .version(2)
                .status("APPROVED")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        TaskPlanDto dto = TaskPlanDto.builder()
                .planContent("Step 1: Do something")
                .status("DRAFT")
                .version(5)
                .build();

        String str = dto.toString();
        assertTrue(str.contains("Step 1: Do something"));
        assertTrue(str.contains("DRAFT"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        TaskPlanDto dto = TaskPlanDto.builder()
                .id(null)
                .tenantId(null)
                .taskId(null)
                .conversationId(null)
                .version(null)
                .planContent(null)
                .status(null)
                .approvedBy(null)
                .approvedAt(null)
                .createdAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getConversationId());
        assertNull(dto.getVersion());
        assertNull(dto.getPlanContent());
        assertNull(dto.getStatus());
        assertNull(dto.getApprovedBy());
        assertNull(dto.getApprovedAt());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_handleUnapprovedPlan_when_approvalFieldsNull() {
        TaskPlanDto dto = TaskPlanDto.builder()
                .version(1)
                .planContent("Initial plan")
                .status("DRAFT")
                .approvedBy(null)
                .approvedAt(null)
                .build();

        assertEquals("DRAFT", dto.getStatus());
        assertNull(dto.getApprovedBy());
        assertNull(dto.getApprovedAt());
    }
}
