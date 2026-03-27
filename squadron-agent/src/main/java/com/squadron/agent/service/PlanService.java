package com.squadron.agent.service;

import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.repository.TaskPlanRepository;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final TaskPlanRepository planRepository;
    private final NatsEventPublisher natsEventPublisher;

    public PlanService(TaskPlanRepository planRepository, NatsEventPublisher natsEventPublisher) {
        this.planRepository = planRepository;
        this.natsEventPublisher = natsEventPublisher;
    }

    /**
     * Creates a new draft plan for a task. Automatically increments the version
     * based on the latest existing plan.
     */
    public TaskPlan createPlan(UUID tenantId, UUID taskId, UUID conversationId, String planContent) {
        int nextVersion = planRepository.findFirstByTaskIdOrderByVersionDesc(taskId)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        TaskPlan plan = TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(nextVersion)
                .planContent(planContent)
                .status("DRAFT")
                .build();

        TaskPlan saved = planRepository.save(plan);
        log.info("Created plan v{} for task {} (id={})", nextVersion, taskId, saved.getId());

        return saved;
    }

    /**
     * Approves or rejects a plan. On approval, publishes an event to notify
     * downstream services.
     */
    public TaskPlan approvePlan(UUID planId, UUID approvedBy) {
        TaskPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskPlan", planId));

        plan.setStatus("APPROVED");
        plan.setApprovedBy(approvedBy);
        plan.setApprovedAt(Instant.now());

        TaskPlan saved = planRepository.save(plan);

        // Publish plan approval event
        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTenantId(plan.getTenantId());
        event.setTaskId(plan.getTaskId());
        event.setConversationId(plan.getConversationId());
        event.setAgentType("PLANNING");
        event.setSuccess(true);
        event.setSource("squadron-agent");

        natsEventPublisher.publishAsync("squadron.agent.plan.approved", event);
        log.info("Plan {} approved by user {}", planId, approvedBy);

        return saved;
    }

    /**
     * Returns the latest plan for a task (by version, descending).
     */
    @Transactional(readOnly = true)
    public TaskPlan getLatestPlan(UUID taskId) {
        return planRepository.findFirstByTaskIdOrderByVersionDesc(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskPlan", "task:" + taskId));
    }

    /**
     * Returns all plan versions for a task, ordered by version descending.
     */
    @Transactional(readOnly = true)
    public List<TaskPlan> listPlans(UUID taskId) {
        return planRepository.findByTaskIdOrderByVersionDesc(taskId);
    }
}
