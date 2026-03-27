package com.squadron.orchestrator.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.common.exception.InvalidStateTransitionException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.TaskStateHistoryRepository;
import com.squadron.orchestrator.repository.TaskWorkflowRepository;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final String NATS_SUBJECT = "squadron.orchestrator.task.state-changed";

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final TaskWorkflowRepository taskWorkflowRepository;
    private final TaskStateHistoryRepository taskStateHistoryRepository;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public WorkflowEngine(WorkflowDefinitionRepository workflowDefinitionRepository,
                          TaskWorkflowRepository taskWorkflowRepository,
                          TaskStateHistoryRepository taskStateHistoryRepository,
                          NatsEventPublisher natsEventPublisher,
                          ObjectMapper objectMapper) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.taskWorkflowRepository = taskWorkflowRepository;
        this.taskStateHistoryRepository = taskStateHistoryRepository;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskWorkflow initializeWorkflow(UUID tenantId, UUID taskId, UUID userId) {
        log.info("Initializing workflow for task {} in tenant {}", taskId, tenantId);

        TaskWorkflow workflow = TaskWorkflow.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState(TaskState.BACKLOG.name())
                .previousState(null)
                .transitionAt(Instant.now())
                .transitionedBy(userId)
                .build();

        workflow = taskWorkflowRepository.save(workflow);

        TaskStateHistory history = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState(null)
                .toState(TaskState.BACKLOG.name())
                .triggeredBy(userId)
                .reason("Workflow initialized")
                .build();
        taskStateHistoryRepository.save(history);

        return workflow;
    }

    @Transactional
    public TaskWorkflow transition(UUID taskId, String targetState, UUID userId, String reason) {
        log.info("Transitioning task {} to state {}", taskId, targetState);

        TaskWorkflow workflow = taskWorkflowRepository.findByTaskIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkflow", taskId));

        WorkflowDefinition definition = resolveWorkflowDefinition(workflow.getTenantId(), null);
        List<Map<String, String>> transitions = parseTransitions(definition.getTransitions());

        String currentState = workflow.getCurrentState();
        boolean allowed = transitions.stream()
                .anyMatch(t -> t.get("from").equals(currentState) && t.get("to").equals(targetState));

        if (!allowed) {
            throw new InvalidStateTransitionException(currentState, targetState);
        }

        String previousState = workflow.getCurrentState();
        workflow.setCurrentState(targetState);
        workflow.setPreviousState(previousState);
        workflow.setTransitionAt(Instant.now());
        workflow.setTransitionedBy(userId);
        workflow = taskWorkflowRepository.save(workflow);

        TaskStateHistory history = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState(previousState)
                .toState(targetState)
                .triggeredBy(userId)
                .reason(reason)
                .build();
        taskStateHistoryRepository.save(history);

        publishStateChangedEvent(workflow.getTenantId(), taskId, previousState, targetState, userId, reason);

        return workflow;
    }

    @Transactional(readOnly = true)
    public boolean isTransitionAllowed(UUID taskId, String targetState) {
        TaskWorkflow workflow = taskWorkflowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkflow", taskId));

        WorkflowDefinition definition = resolveWorkflowDefinition(workflow.getTenantId(), null);
        List<Map<String, String>> transitions = parseTransitions(definition.getTransitions());

        String currentState = workflow.getCurrentState();
        return transitions.stream()
                .anyMatch(t -> t.get("from").equals(currentState) && t.get("to").equals(targetState));
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableTransitions(UUID taskId) {
        TaskWorkflow workflow = taskWorkflowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskWorkflow", taskId));

        WorkflowDefinition definition = resolveWorkflowDefinition(workflow.getTenantId(), null);
        List<Map<String, String>> transitions = parseTransitions(definition.getTransitions());

        String currentState = workflow.getCurrentState();
        return transitions.stream()
                .filter(t -> t.get("from").equals(currentState))
                .map(t -> t.get("to"))
                .collect(Collectors.toList());
    }

    private WorkflowDefinition resolveWorkflowDefinition(UUID tenantId, UUID teamId) {
        if (teamId != null) {
            return workflowDefinitionRepository.findByTenantIdAndTeamIdAndActiveTrue(tenantId, teamId)
                    .orElseGet(() -> resolveTenantOrDefault(tenantId));
        }
        return resolveTenantOrDefault(tenantId);
    }

    private WorkflowDefinition resolveTenantOrDefault(UUID tenantId) {
        return workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId)
                .orElseGet(() -> {
                    UUID systemTenantId = new UUID(0L, 0L);
                    return workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(systemTenantId)
                            .orElseGet(this::createDefaultWorkflowDefinition);
                });
    }

    private WorkflowDefinition createDefaultWorkflowDefinition() {
        log.info("Creating system default workflow definition");

        List<String> states = Arrays.stream(TaskState.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        List<Map<String, String>> transitions = new ArrayList<>();
        transitions.add(Map.of("from", "BACKLOG", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "PLANNING"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "BACKLOG"));
        transitions.add(Map.of("from", "PLANNING", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "PLANNING", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "REVIEW"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "PLANNING"));
        transitions.add(Map.of("from", "REVIEW", "to", "QA"));
        transitions.add(Map.of("from", "REVIEW", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "QA", "to", "MERGE"));
        transitions.add(Map.of("from", "QA", "to", "REVIEW"));
        transitions.add(Map.of("from", "MERGE", "to", "DONE"));
        transitions.add(Map.of("from", "MERGE", "to", "QA"));

        try {
            UUID systemTenantId = new UUID(0L, 0L);
            WorkflowDefinition definition = WorkflowDefinition.builder()
                    .tenantId(systemTenantId)
                    .teamId(null)
                    .name("System Default Workflow")
                    .states(objectMapper.writeValueAsString(states))
                    .transitions(objectMapper.writeValueAsString(transitions))
                    .hooks("{}")
                    .active(true)
                    .build();
            return workflowDefinitionRepository.save(definition);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize default workflow definition", e);
        }
    }

    private List<Map<String, String>> parseTransitions(String transitionsJson) {
        try {
            return objectMapper.readValue(transitionsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse workflow transitions", e);
        }
    }

    private void publishStateChangedEvent(UUID tenantId, UUID taskId, String fromState,
                                           String toState, UUID userId, String reason) {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTenantId(tenantId);
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setTriggeredBy(userId);
        event.setReason(reason);

        try {
            natsEventPublisher.publishAsync(NATS_SUBJECT, event);
        } catch (Exception e) {
            log.warn("Failed to publish state changed event for task {}: {}", taskId, e.getMessage());
        }
    }
}
