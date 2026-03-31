package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectWorkflowMappingRepository extends JpaRepository<ProjectWorkflowMapping, UUID> {

    List<ProjectWorkflowMapping> findByProjectId(UUID projectId);

    Optional<ProjectWorkflowMapping> findByProjectIdAndInternalState(UUID projectId, String internalState);

    void deleteByProjectId(UUID projectId);

    void deleteByProjectIdAndInternalState(UUID projectId, String internalState);
}
