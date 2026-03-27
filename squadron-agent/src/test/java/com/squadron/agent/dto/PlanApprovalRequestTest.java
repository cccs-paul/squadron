package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlanApprovalRequestTest {

    @Test
    void should_buildPlanApprovalRequest_when_usingBuilder() {
        UUID planId = UUID.randomUUID();

        PlanApprovalRequest request = PlanApprovalRequest.builder()
                .planId(planId)
                .approved(true)
                .build();

        assertEquals(planId, request.getPlanId());
        assertTrue(request.isApproved());
    }

    @Test
    void should_createPlanApprovalRequest_when_usingNoArgsConstructor() {
        PlanApprovalRequest request = new PlanApprovalRequest();
        assertNull(request.getPlanId());
        assertFalse(request.isApproved());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        PlanApprovalRequest request = new PlanApprovalRequest();
        UUID planId = UUID.randomUUID();

        request.setPlanId(planId);
        request.setApproved(true);

        assertEquals(planId, request.getPlanId());
        assertTrue(request.isApproved());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID planId = UUID.randomUUID();
        PlanApprovalRequest r1 = PlanApprovalRequest.builder().planId(planId).approved(true).build();
        PlanApprovalRequest r2 = PlanApprovalRequest.builder().planId(planId).approved(true).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentApprovalStatus() {
        UUID planId = UUID.randomUUID();
        PlanApprovalRequest r1 = PlanApprovalRequest.builder().planId(planId).approved(true).build();
        PlanApprovalRequest r2 = PlanApprovalRequest.builder().planId(planId).approved(false).build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_createPlanApprovalRequest_when_usingAllArgsConstructor() {
        UUID planId = UUID.randomUUID();
        PlanApprovalRequest request = new PlanApprovalRequest(planId, false);

        assertEquals(planId, request.getPlanId());
        assertFalse(request.isApproved());
    }

    @Test
    void should_defaultApprovedToFalse_when_builderWithoutSetting() {
        PlanApprovalRequest request = PlanApprovalRequest.builder()
                .planId(UUID.randomUUID())
                .build();

        assertFalse(request.isApproved());
    }
}
