package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateWorkflowDefinitionRequest;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    public WorkflowDefinitionService(WorkflowDefinitionRepository workflowDefinitionRepository) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
    }

    public WorkflowDefinition create(CreateWorkflowDefinitionRequest request) {
        log.info("Creating workflow definition '{}' for tenant {}", request.getName(), request.getTenantId());

        WorkflowDefinition definition = WorkflowDefinition.builder()
                .tenantId(request.getTenantId())
                .teamId(request.getTeamId())
                .name(request.getName())
                .states(request.getStates())
                .transitions(request.getTransitions())
                .hooks(request.getHooks() != null ? request.getHooks() : "{}")
                .active(true)
                .build();

        return workflowDefinitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition getById(UUID id) {
        return workflowDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listByTenant(UUID tenantId) {
        return workflowDefinitionRepository.findByTenantId(tenantId);
    }

    public WorkflowDefinition update(UUID id, CreateWorkflowDefinitionRequest request) {
        WorkflowDefinition definition = getById(id);

        if (request.getName() != null) {
            definition.setName(request.getName());
        }
        if (request.getStates() != null) {
            definition.setStates(request.getStates());
        }
        if (request.getTransitions() != null) {
            definition.setTransitions(request.getTransitions());
        }
        if (request.getHooks() != null) {
            definition.setHooks(request.getHooks());
        }
        if (request.getTeamId() != null) {
            definition.setTeamId(request.getTeamId());
        }

        return workflowDefinitionRepository.save(definition);
    }

    public void delete(UUID id) {
        WorkflowDefinition definition = getById(id);
        workflowDefinitionRepository.delete(definition);
        log.info("Deleted workflow definition '{}' ({})", definition.getName(), id);
    }

    public void activate(UUID id) {
        WorkflowDefinition definition = getById(id);
        definition.setActive(true);
        workflowDefinitionRepository.save(definition);
        log.info("Activated workflow definition '{}' ({})", definition.getName(), id);
    }

    public void deactivate(UUID id) {
        WorkflowDefinition definition = getById(id);
        definition.setActive(false);
        workflowDefinitionRepository.save(definition);
        log.info("Deactivated workflow definition '{}' ({})", definition.getName(), id);
    }
}
