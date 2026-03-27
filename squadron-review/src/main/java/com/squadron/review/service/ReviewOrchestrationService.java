package com.squadron.review.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReviewOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrationService.class);

    private final ReviewService reviewService;
    private final ReviewPolicyService reviewPolicyService;
    private final ReviewGateService reviewGateService;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public ReviewOrchestrationService(ReviewService reviewService,
                                      ReviewPolicyService reviewPolicyService,
                                      ReviewGateService reviewGateService,
                                      NatsEventPublisher natsEventPublisher,
                                      ObjectMapper objectMapper) {
        this.reviewService = reviewService;
        this.reviewPolicyService = reviewPolicyService;
        this.reviewGateService = reviewGateService;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    public ReviewOrchestrationResult orchestrateReview(UUID taskId, UUID tenantId, UUID teamId) {
        log.info("Orchestrating review for task {} tenant {} team {}", taskId, tenantId, teamId);

        ReviewPolicy policy = reviewPolicyService.resolvePolicy(tenantId, teamId);

        List<UUID> createdReviewIds = new ArrayList<>();
        boolean aiReviewCreated = false;

        // Create AI review if required by policy
        if (Boolean.TRUE.equals(policy.getRequireAiReview())) {
            CreateReviewRequest aiRequest = CreateReviewRequest.builder()
                    .tenantId(tenantId)
                    .taskId(taskId)
                    .reviewerType("AI")
                    .build();
            Review aiReview = reviewService.createReview(aiRequest);
            createdReviewIds.add(aiReview.getId());
            aiReviewCreated = true;
            log.info("Created AI review {} for task {}", aiReview.getId(), taskId);
        }

        // Parse auto-request reviewers from policy
        List<UUID> reviewerUuids = parseAutoRequestReviewers(policy.getAutoRequestReviewers());

        int pendingHumanReviews = 0;

        if (!reviewerUuids.isEmpty()) {
            // Create a HUMAN review for each auto-requested reviewer
            for (UUID reviewerId : reviewerUuids) {
                CreateReviewRequest humanRequest = CreateReviewRequest.builder()
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .reviewerId(reviewerId)
                        .reviewerType("HUMAN")
                        .build();
                Review humanReview = reviewService.createReview(humanRequest);
                createdReviewIds.add(humanReview.getId());
                log.info("Created HUMAN review {} for task {} assigned to reviewer {}",
                        humanReview.getId(), taskId, reviewerId);
            }
        } else if (policy.getMinHumanApprovals() > 0) {
            // No auto-request reviewers configured, but human approvals are required
            pendingHumanReviews = policy.getMinHumanApprovals();
            log.info("No auto-request reviewers configured for task {}; {} human approvals pending",
                    taskId, pendingHumanReviews);
        }

        // Check if the policy is already met (e.g., zero approvals needed)
        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        return ReviewOrchestrationResult.builder()
                .taskId(taskId)
                .createdReviewIds(createdReviewIds)
                .aiReviewCreated(aiReviewCreated)
                .pendingHumanReviews(pendingHumanReviews)
                .policyMet(summary.isPolicyMet())
                .build();
    }

    public boolean checkAndTransition(UUID taskId, UUID tenantId, UUID teamId) {
        log.info("Checking review gate and transitioning for task {} tenant {} team {}", taskId, tenantId, teamId);

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        if (summary.isPolicyMet()) {
            SquadronEvent event = new SquadronEvent();
            event.setEventType("REVIEW_GATE_PASSED");
            event.setTenantId(tenantId);
            event.setSource("squadron-review");

            natsEventPublisher.publish("squadron.review.gate.passed", event);
            log.info("Review gate passed for task {}; event published", taskId);
        }

        return summary.isPolicyMet();
    }

    public List<UUID> autoAssignReviewers(UUID taskId, UUID tenantId, UUID teamId) {
        log.info("Auto-assigning reviewers for task {} tenant {} team {}", taskId, tenantId, teamId);

        ReviewPolicy policy = reviewPolicyService.resolvePolicy(tenantId, teamId);
        return parseAutoRequestReviewers(policy.getAutoRequestReviewers());
    }

    private List<UUID> parseAutoRequestReviewers(String autoRequestReviewersJson) {
        if (autoRequestReviewersJson == null || autoRequestReviewersJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<String> uuidStrings = objectMapper.readValue(autoRequestReviewersJson,
                    new TypeReference<List<String>>() {});
            List<UUID> uuids = new ArrayList<>();
            for (String uuidString : uuidStrings) {
                uuids.add(UUID.fromString(uuidString));
            }
            return uuids;
        } catch (Exception e) {
            log.warn("Failed to parse autoRequestReviewers JSON: {}", autoRequestReviewersJson, e);
            return Collections.emptyList();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewOrchestrationResult {
        private UUID taskId;
        private List<UUID> createdReviewIds;
        private boolean aiReviewCreated;
        private int pendingHumanReviews;
        private boolean policyMet;
    }
}
