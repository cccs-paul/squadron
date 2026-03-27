package com.squadron.agent.service;

import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.repository.TaskPlanRepository;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private TaskPlanRepository planRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, natsEventPublisher);
    }

    @Test
    void should_createPlan_when_noExistingPlans() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(planRepository.findFirstByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(Optional.empty());

        TaskPlan savedPlan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(1)
                .planContent("Step 1: Do the thing")
                .status("DRAFT")
                .build();

        when(planRepository.save(any(TaskPlan.class))).thenReturn(savedPlan);

        TaskPlan result = planService.createPlan(tenantId, taskId, conversationId, "Step 1: Do the thing");

        assertNotNull(result);
        assertEquals(1, result.getVersion());
        assertEquals("DRAFT", result.getStatus());
        assertEquals("Step 1: Do the thing", result.getPlanContent());
        verify(planRepository).save(any(TaskPlan.class));
    }

    @Test
    void should_createPlanWithIncrementedVersion_when_existingPlanExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TaskPlan existingPlan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .version(2)
                .status("DRAFT")
                .build();

        when(planRepository.findFirstByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(Optional.of(existingPlan));

        TaskPlan savedPlan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(3)
                .planContent("Updated plan")
                .status("DRAFT")
                .build();

        when(planRepository.save(any(TaskPlan.class))).thenReturn(savedPlan);

        TaskPlan result = planService.createPlan(tenantId, taskId, conversationId, "Updated plan");

        assertNotNull(result);
        assertEquals(3, result.getVersion());
    }

    @Test
    void should_approvePlan_when_planExists() {
        UUID planId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        TaskPlan plan = TaskPlan.builder()
                .id(planId)
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversationId)
                .version(1)
                .planContent("Plan content")
                .status("DRAFT")
                .build();

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(TaskPlan.class))).thenReturn(plan);

        TaskPlan result = planService.approvePlan(planId, approvedBy);

        assertEquals("APPROVED", result.getStatus());
        assertEquals(approvedBy, result.getApprovedBy());
        assertNotNull(result.getApprovedAt());
        verify(natsEventPublisher).publishAsync(anyString(), any());
    }

    @Test
    void should_throwNotFound_when_approvingNonexistentPlan() {
        UUID planId = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();

        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> planService.approvePlan(planId, approvedBy));
    }

    @Test
    void should_getLatestPlan_when_planExists() {
        UUID taskId = UUID.randomUUID();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .version(3)
                .planContent("Latest plan")
                .status("DRAFT")
                .build();

        when(planRepository.findFirstByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(Optional.of(plan));

        TaskPlan result = planService.getLatestPlan(taskId);

        assertNotNull(result);
        assertEquals(3, result.getVersion());
        assertEquals("Latest plan", result.getPlanContent());
    }

    @Test
    void should_throwNotFound_when_noPlansForTask() {
        UUID taskId = UUID.randomUUID();

        when(planRepository.findFirstByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> planService.getLatestPlan(taskId));
    }

    @Test
    void should_listPlans_when_plansExist() {
        UUID taskId = UUID.randomUUID();

        TaskPlan p1 = TaskPlan.builder().id(UUID.randomUUID()).taskId(taskId)
                .version(2).planContent("plan 2").status("DRAFT").build();
        TaskPlan p2 = TaskPlan.builder().id(UUID.randomUUID()).taskId(taskId)
                .version(1).planContent("plan 1").status("APPROVED").build();

        when(planRepository.findByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(List.of(p1, p2));

        List<TaskPlan> result = planService.listPlans(taskId);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersion());
        assertEquals(1, result.get(1).getVersion());
    }

    @Test
    void should_returnEmptyList_when_noPlansForTask() {
        UUID taskId = UUID.randomUUID();

        when(planRepository.findByTaskIdOrderByVersionDesc(taskId))
                .thenReturn(List.of());

        List<TaskPlan> result = planService.listPlans(taskId);

        assertTrue(result.isEmpty());
    }
}
