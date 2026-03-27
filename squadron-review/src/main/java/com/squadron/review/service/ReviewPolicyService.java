package com.squadron.review.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.ReviewPolicyDto;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.repository.ReviewPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ReviewPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ReviewPolicyService.class);

    private final ReviewPolicyRepository reviewPolicyRepository;

    public ReviewPolicyService(ReviewPolicyRepository reviewPolicyRepository) {
        this.reviewPolicyRepository = reviewPolicyRepository;
    }

    public ReviewPolicy createOrUpdatePolicy(ReviewPolicyDto dto) {
        log.info("Creating/updating review policy for tenant {} team {}", dto.getTenantId(), dto.getTeamId());

        Optional<ReviewPolicy> existing;
        if (dto.getTeamId() != null) {
            existing = reviewPolicyRepository.findByTenantIdAndTeamId(dto.getTenantId(), dto.getTeamId());
        } else {
            existing = reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(dto.getTenantId());
        }

        ReviewPolicy policy;
        if (existing.isPresent()) {
            policy = existing.get();
            if (dto.getMinHumanApprovals() != null) {
                policy.setMinHumanApprovals(dto.getMinHumanApprovals());
            }
            if (dto.getRequireAiReview() != null) {
                policy.setRequireAiReview(dto.getRequireAiReview());
            }
            if (dto.getSelfReviewAllowed() != null) {
                policy.setSelfReviewAllowed(dto.getSelfReviewAllowed());
            }
            if (dto.getAutoRequestReviewers() != null) {
                policy.setAutoRequestReviewers(dto.getAutoRequestReviewers());
            }
            if (dto.getReviewChecklist() != null) {
                policy.setReviewChecklist(dto.getReviewChecklist());
            }
        } else {
            policy = ReviewPolicy.builder()
                    .tenantId(dto.getTenantId())
                    .teamId(dto.getTeamId())
                    .minHumanApprovals(dto.getMinHumanApprovals() != null ? dto.getMinHumanApprovals() : 1)
                    .requireAiReview(dto.getRequireAiReview() != null ? dto.getRequireAiReview() : true)
                    .selfReviewAllowed(dto.getSelfReviewAllowed() != null ? dto.getSelfReviewAllowed() : true)
                    .autoRequestReviewers(dto.getAutoRequestReviewers())
                    .reviewChecklist(dto.getReviewChecklist())
                    .build();
        }

        return reviewPolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public ReviewPolicy resolvePolicy(UUID tenantId, UUID teamId) {
        if (teamId != null) {
            Optional<ReviewPolicy> teamPolicy = reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId);
            if (teamPolicy.isPresent()) {
                return teamPolicy.get();
            }
        }

        return reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId)
                .orElseGet(() -> ReviewPolicy.builder()
                        .tenantId(tenantId)
                        .minHumanApprovals(1)
                        .requireAiReview(true)
                        .selfReviewAllowed(true)
                        .build());
    }

    @Transactional(readOnly = true)
    public ReviewPolicy getPolicy(UUID id) {
        return reviewPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewPolicy", id));
    }
}
