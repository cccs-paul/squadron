package com.squadron.review.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.ReviewPolicyDto;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.repository.ReviewPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewPolicyServiceTest {

    @Mock
    private ReviewPolicyRepository reviewPolicyRepository;

    private ReviewPolicyService reviewPolicyService;

    @BeforeEach
    void setUp() {
        reviewPolicyService = new ReviewPolicyService(reviewPolicyRepository);
    }

    @Test
    void should_createPolicy_when_noneExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicyDto dto = ReviewPolicyDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .selfReviewAllowed(false)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId))
                .thenReturn(Optional.empty());
        when(reviewPolicyRepository.save(any(ReviewPolicy.class)))
                .thenAnswer(invocation -> {
                    ReviewPolicy p = invocation.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        ReviewPolicy result = reviewPolicyService.createOrUpdatePolicy(dto);

        assertNotNull(result);
        assertEquals(2, result.getMinHumanApprovals());
        assertTrue(result.getRequireAiReview());
        assertEquals(false, result.getSelfReviewAllowed());
        verify(reviewPolicyRepository).save(any(ReviewPolicy.class));
    }

    @Test
    void should_updatePolicy_when_existsForTeam() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy existing = ReviewPolicy.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        ReviewPolicyDto dto = ReviewPolicyDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(3)
                .requireAiReview(false)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId))
                .thenReturn(Optional.of(existing));
        when(reviewPolicyRepository.save(any(ReviewPolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewPolicy result = reviewPolicyService.createOrUpdatePolicy(dto);

        assertEquals(3, result.getMinHumanApprovals());
        assertEquals(false, result.getRequireAiReview());
        assertTrue(result.getSelfReviewAllowed()); // unchanged
    }

    @Test
    void should_createTenantLevelPolicy_when_teamIdNull() {
        UUID tenantId = UUID.randomUUID();

        ReviewPolicyDto dto = ReviewPolicyDto.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId))
                .thenReturn(Optional.empty());
        when(reviewPolicyRepository.save(any(ReviewPolicy.class)))
                .thenAnswer(invocation -> {
                    ReviewPolicy p = invocation.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });

        ReviewPolicy result = reviewPolicyService.createOrUpdatePolicy(dto);

        assertNotNull(result);
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void should_resolveTeamPolicy_when_teamPolicyExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy teamPolicy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId))
                .thenReturn(Optional.of(teamPolicy));

        ReviewPolicy result = reviewPolicyService.resolvePolicy(tenantId, teamId);

        assertEquals(teamPolicy, result);
        assertEquals(2, result.getMinHumanApprovals());
    }

    @Test
    void should_fallbackToTenantPolicy_when_noTeamPolicy() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy tenantPolicy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(false)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId))
                .thenReturn(Optional.empty());
        when(reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId))
                .thenReturn(Optional.of(tenantPolicy));

        ReviewPolicy result = reviewPolicyService.resolvePolicy(tenantId, teamId);

        assertEquals(tenantPolicy, result);
    }

    @Test
    void should_returnDefaultPolicy_when_noPolicyExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        when(reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId))
                .thenReturn(Optional.empty());
        when(reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId))
                .thenReturn(Optional.empty());

        ReviewPolicy result = reviewPolicyService.resolvePolicy(tenantId, teamId);

        assertEquals(tenantId, result.getTenantId());
        assertEquals(1, result.getMinHumanApprovals());
        assertTrue(result.getRequireAiReview());
        assertTrue(result.getSelfReviewAllowed());
    }

    @Test
    void should_resolveWithNullTeam_when_teamIdNull() {
        UUID tenantId = UUID.randomUUID();

        ReviewPolicy tenantPolicy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId))
                .thenReturn(Optional.of(tenantPolicy));

        ReviewPolicy result = reviewPolicyService.resolvePolicy(tenantId, null);

        assertEquals(tenantPolicy, result);
    }

    @Test
    void should_getPolicy_when_exists() {
        UUID policyId = UUID.randomUUID();
        ReviewPolicy policy = ReviewPolicy.builder()
                .id(policyId)
                .tenantId(UUID.randomUUID())
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        ReviewPolicy result = reviewPolicyService.getPolicy(policyId);

        assertEquals(policyId, result.getId());
    }

    @Test
    void should_throwNotFound_when_policyMissing() {
        UUID policyId = UUID.randomUUID();
        when(reviewPolicyRepository.findById(policyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewPolicyService.getPolicy(policyId));
    }
}
