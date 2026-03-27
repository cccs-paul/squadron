package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateWorkflowDefinitionRequest;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    private WorkflowDefinitionService workflowDefinitionService;

    @BeforeEach
    void setUp() {
        workflowDefinitionService = new WorkflowDefinitionService(workflowDefinitionRepository);
    }

    @Test
    void should_create_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Default Workflow")
                .states("[\"BACKLOG\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"IN_PROGRESS\"}]")
                .hooks("{\"onEnter\":{}}")
                .build();

        WorkflowDefinition saved = WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Default Workflow")
                .states("[\"BACKLOG\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"IN_PROGRESS\"}]")
                .hooks("{\"onEnter\":{}}")
                .active(true)
                .build();

        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(saved);

        WorkflowDefinition result = workflowDefinitionService.create(request);

        assertNotNull(result);
        assertEquals("Default Workflow", result.getName());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(teamId, result.getTeamId());
        assertTrue(result.getActive());
        verify(workflowDefinitionRepository).save(any(WorkflowDefinition.class));
    }

    @Test
    void should_create_withDefaultHooks_when_hooksNull() {
        UUID tenantId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .name("Simple Workflow")
                .states("[\"TODO\",\"DONE\"]")
                .transitions("[{\"from\":\"TODO\",\"to\":\"DONE\"}]")
                .hooks(null)
                .build();

        WorkflowDefinition saved = WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Simple Workflow")
                .states("[\"TODO\",\"DONE\"]")
                .transitions("[{\"from\":\"TODO\",\"to\":\"DONE\"}]")
                .hooks("{}")
                .active(true)
                .build();

        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(saved);

        WorkflowDefinition result = workflowDefinitionService.create(request);

        assertNotNull(result);
        assertEquals("{}", result.getHooks());
    }

    @Test
    void should_getById_when_exists() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(id)
                .name("Found Workflow")
                .states("[\"BACKLOG\"]")
                .transitions("[]")
                .build();

        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.of(definition));

        WorkflowDefinition result = workflowDefinitionService.getById(id);

        assertEquals(id, result.getId());
        assertEquals("Found Workflow", result.getName());
    }

    @Test
    void should_throwNotFound_when_getByIdMissing() {
        UUID id = UUID.randomUUID();
        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workflowDefinitionService.getById(id));
    }

    @Test
    void should_listByTenant() {
        UUID tenantId = UUID.randomUUID();
        List<WorkflowDefinition> definitions = List.of(
                WorkflowDefinition.builder().id(UUID.randomUUID()).tenantId(tenantId).name("WF1").states("[]").transitions("[]").build(),
                WorkflowDefinition.builder().id(UUID.randomUUID()).tenantId(tenantId).name("WF2").states("[]").transitions("[]").build()
        );

        when(workflowDefinitionRepository.findByTenantId(tenantId)).thenReturn(definitions);

        List<WorkflowDefinition> result = workflowDefinitionService.listByTenant(tenantId);

        assertEquals(2, result.size());
        assertEquals("WF1", result.get(0).getName());
        assertEquals("WF2", result.get(1).getName());
    }

    @Test
    void should_update_when_allFieldsProvided() {
        UUID id = UUID.randomUUID();
        UUID newTeamId = UUID.randomUUID();

        WorkflowDefinition existing = WorkflowDefinition.builder()
                .id(id)
                .tenantId(UUID.randomUUID())
                .name("Old Name")
                .states("[\"OLD\"]")
                .transitions("[{\"from\":\"OLD\",\"to\":\"OLD\"}]")
                .hooks("{}")
                .active(true)
                .build();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(existing.getTenantId())
                .teamId(newTeamId)
                .name("New Name")
                .states("[\"NEW_STATE\"]")
                .transitions("[{\"from\":\"NEW\",\"to\":\"NEW\"}]")
                .hooks("{\"onEnter\":{}}")
                .build();

        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(existing);

        WorkflowDefinition result = workflowDefinitionService.update(id, request);

        assertEquals("New Name", existing.getName());
        assertEquals("[\"NEW_STATE\"]", existing.getStates());
        assertEquals("[{\"from\":\"NEW\",\"to\":\"NEW\"}]", existing.getTransitions());
        assertEquals("{\"onEnter\":{}}", existing.getHooks());
        assertEquals(newTeamId, existing.getTeamId());
        verify(workflowDefinitionRepository).save(existing);
    }

    @Test
    void should_throwNotFound_when_updatingMissing() {
        UUID id = UUID.randomUUID();
        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.empty());

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(UUID.randomUUID())
                .name("X")
                .states("[]")
                .transitions("[]")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> workflowDefinitionService.update(id, request));
    }

    @Test
    void should_delete_when_exists() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(id)
                .name("To Delete")
                .states("[]")
                .transitions("[]")
                .build();

        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.of(definition));

        workflowDefinitionService.delete(id);

        verify(workflowDefinitionRepository).delete(definition);
    }

    @Test
    void should_throwNotFound_when_deletingMissing() {
        UUID id = UUID.randomUUID();
        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workflowDefinitionService.delete(id));
    }

    @Test
    void should_activate_when_exists() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(id)
                .name("Inactive WF")
                .states("[]")
                .transitions("[]")
                .active(false)
                .build();

        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.of(definition));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(definition);

        workflowDefinitionService.activate(id);

        assertTrue(definition.getActive());
        verify(workflowDefinitionRepository).save(definition);
    }

    @Test
    void should_deactivate_when_exists() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(id)
                .name("Active WF")
                .states("[]")
                .transitions("[]")
                .active(true)
                .build();

        when(workflowDefinitionRepository.findById(id)).thenReturn(Optional.of(definition));
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class))).thenReturn(definition);

        workflowDefinitionService.deactivate(id);

        assertFalse(definition.getActive());
        verify(workflowDefinitionRepository).save(definition);
    }
}
