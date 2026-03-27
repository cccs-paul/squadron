package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectId(UUID projectId);

    List<Task> findByTenantId(UUID tenantId);

    List<Task> findByTeamId(UUID teamId);

    Optional<Task> findByExternalId(String externalId);

    List<Task> findByAssigneeId(UUID assigneeId);
}
