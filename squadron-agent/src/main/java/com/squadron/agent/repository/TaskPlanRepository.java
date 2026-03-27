package com.squadron.agent.repository;

import com.squadron.agent.entity.TaskPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskPlanRepository extends JpaRepository<TaskPlan, UUID> {

    List<TaskPlan> findByTaskIdOrderByVersionDesc(UUID taskId);

    Optional<TaskPlan> findByTaskIdAndStatus(UUID taskId, String status);

    Optional<TaskPlan> findFirstByTaskIdOrderByVersionDesc(UUID taskId);
}
