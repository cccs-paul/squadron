package com.squadron.git.repository;

import com.squadron.git.entity.BranchStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchStrategyRepository extends JpaRepository<BranchStrategy, UUID> {

    Optional<BranchStrategy> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    Optional<BranchStrategy> findByTenantIdAndProjectIdIsNull(UUID tenantId);

    List<BranchStrategy> findByTenantId(UUID tenantId);
}
