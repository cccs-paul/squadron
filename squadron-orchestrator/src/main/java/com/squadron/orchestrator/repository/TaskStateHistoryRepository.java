package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.TaskStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskStateHistoryRepository extends JpaRepository<TaskStateHistory, UUID> {

    List<TaskStateHistory> findByTaskWorkflowIdOrderByCreatedAtDesc(UUID taskWorkflowId);
}
