package com.squadron.workspace.repository;

import com.squadron.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByTaskId(UUID taskId);

    List<Workspace> findByUserId(UUID userId);

    List<Workspace> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<Workspace> findByTaskIdAndStatus(UUID taskId, String status);

    @Query("SELECT w FROM Workspace w WHERE w.status IN ('READY', 'ACTIVE') AND w.createdAt < :threshold")
    List<Workspace> findStaleWorkspaces(@Param("threshold") Instant threshold);
}
