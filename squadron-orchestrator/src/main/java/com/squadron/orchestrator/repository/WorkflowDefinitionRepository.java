package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByTenantIdAndTeamIdAndActiveTrue(UUID tenantId, UUID teamId);

    Optional<WorkflowDefinition> findByTenantIdAndTeamIdIsNullAndActiveTrue(UUID tenantId);

    List<WorkflowDefinition> findByTenantId(UUID tenantId);
}
