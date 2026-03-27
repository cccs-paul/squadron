package com.squadron.review.repository;

import com.squadron.review.entity.ReviewPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewPolicyRepository extends JpaRepository<ReviewPolicy, UUID> {

    Optional<ReviewPolicy> findByTenantIdAndTeamId(UUID tenantId, UUID teamId);

    Optional<ReviewPolicy> findByTenantIdAndTeamIdIsNull(UUID tenantId);
}
