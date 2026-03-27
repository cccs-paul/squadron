package com.squadron.review.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewPolicyTest {

    @Test
    void should_buildReviewPolicy_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(false)
                .selfReviewAllowed(false)
                .autoRequestReviewers("[\"user1\",\"user2\"]")
                .reviewChecklist("[\"security\",\"performance\"]")
                .build();

        assertEquals(tenantId, policy.getTenantId());
        assertEquals(teamId, policy.getTeamId());
        assertEquals(2, policy.getMinHumanApprovals());
        assertEquals(false, policy.getRequireAiReview());
        assertEquals(false, policy.getSelfReviewAllowed());
        assertEquals("[\"user1\",\"user2\"]", policy.getAutoRequestReviewers());
        assertEquals("[\"security\",\"performance\"]", policy.getReviewChecklist());
    }

    @Test
    void should_useDefaults_when_notExplicitlySet() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .build();

        assertEquals(1, policy.getMinHumanApprovals());
        assertTrue(policy.getRequireAiReview());
        assertTrue(policy.getSelfReviewAllowed());
    }

    @Test
    void should_allowNullTeamId_when_tenantLevelPolicy() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .build();

        assertNull(policy.getTeamId());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .build();

        policy.onCreate();

        assertNotNull(policy.getCreatedAt());
        assertNotNull(policy.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .build();

        policy.onCreate();
        Instant original = policy.getUpdatedAt();

        policy.onUpdate();

        assertNotNull(policy.getUpdatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewPolicy policy = new ReviewPolicy();
        assertNull(policy.getId());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewPolicy policy = new ReviewPolicy(id, tenantId, teamId, 3, false, false,
                "[\"admin\"]", "[\"testing\"]", now, now);

        assertEquals(id, policy.getId());
        assertEquals(tenantId, policy.getTenantId());
        assertEquals(teamId, policy.getTeamId());
        assertEquals(3, policy.getMinHumanApprovals());
        assertEquals(false, policy.getRequireAiReview());
        assertEquals(false, policy.getSelfReviewAllowed());
    }

    @Test
    void should_supportSetters() {
        ReviewPolicy policy = new ReviewPolicy();
        policy.setMinHumanApprovals(5);
        policy.setRequireAiReview(false);

        assertEquals(5, policy.getMinHumanApprovals());
        assertEquals(false, policy.getRequireAiReview());
    }
}
