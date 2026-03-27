package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.TaskWorkflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskWorkflowRepository extends JpaRepository<TaskWorkflow, UUID> {

    Optional<TaskWorkflow> findByTaskId(UUID taskId);

    List<TaskWorkflow> findByCurrentState(String state);

    List<TaskWorkflow> findByTenantIdAndCurrentState(UUID tenantId, String state);

    List<TaskWorkflow> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tw FROM TaskWorkflow tw WHERE tw.taskId = :taskId")
    Optional<TaskWorkflow> findByTaskIdForUpdate(@Param("taskId") UUID taskId);
}
