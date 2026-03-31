package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.WorkflowMappingDto;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import com.squadron.orchestrator.repository.ProjectRepository;
import com.squadron.orchestrator.repository.ProjectWorkflowMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkflowMappingServiceTest {

    @Mock
    private ProjectWorkflowMappingRepository mappingRepository;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectWorkflowMappingService service;

    private UUID tenantId;
    private UUID projectId;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectWorkflowMappingService(mappingRepository, projectRepository);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .teamId(UUID.randomUUID())
                .name("Test Project")
                .build();
    }

    @Test
    void should_getMappings_when_projectHasMappings() {
        ProjectWorkflowMapping m1 = ProjectWorkflowMapping.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .projectId(projectId)
                .internalState("REVIEW")
                .externalStatus("Code Review")
                .build();
        ProjectWorkflowMapping m2 = ProjectWorkflowMapping.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .projectId(projectId)
                .internalState("DONE")
                .externalStatus("Closed")
                .build();

        when(mappingRepository.findByProjectId(projectId)).thenReturn(List.of(m1, m2));

        List<WorkflowMappingDto> result = service.getMappings(projectId);

        assertEquals(2, result.size());
        assertEquals("REVIEW", result.get(0).getInternalState());
        assertEquals("Code Review", result.get(0).getExternalStatus());
        assertEquals("DONE", result.get(1).getInternalState());
        assertEquals("Closed", result.get(1).getExternalStatus());
    }

    @Test
    void should_returnEmptyList_when_projectHasNoMappings() {
        when(mappingRepository.findByProjectId(projectId)).thenReturn(Collections.emptyList());

        List<WorkflowMappingDto> result = service.getMappings(projectId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_saveMappings_when_validRequest() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        doNothing().when(mappingRepository).deleteByProjectId(projectId);

        List<WorkflowMappingDto> input = List.of(
                WorkflowMappingDto.builder().internalState("BACKLOG").externalStatus("To Do").build(),
                WorkflowMappingDto.builder().internalState("REVIEW").externalStatus("In Review").build(),
                WorkflowMappingDto.builder().internalState("DONE").externalStatus("Done").build()
        );

        when(mappingRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<ProjectWorkflowMapping> entities = inv.getArgument(0);
            return entities;
        });

        List<WorkflowMappingDto> result = service.saveMappings(projectId, input);

        assertEquals(3, result.size());
        assertEquals("BACKLOG", result.get(0).getInternalState());
        assertEquals("To Do", result.get(0).getExternalStatus());
        verify(mappingRepository).deleteByProjectId(projectId);
        verify(mappingRepository).saveAll(anyList());
    }

    @Test
    void should_saveMappings_withEmptyList() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        doNothing().when(mappingRepository).deleteByProjectId(projectId);
        when(mappingRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        List<WorkflowMappingDto> result = service.saveMappings(projectId, Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(mappingRepository).deleteByProjectId(projectId);
    }

    @Test
    void should_throwNotFound_when_projectDoesNotExist() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        List<WorkflowMappingDto> input = List.of(
                WorkflowMappingDto.builder().internalState("REVIEW").externalStatus("Review").build()
        );

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveMappings(projectId, input));

        verify(mappingRepository, never()).deleteByProjectId(any());
        verify(mappingRepository, never()).saveAll(anyList());
    }

    @Test
    void should_throwIllegalArgument_when_invalidInternalState() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        List<WorkflowMappingDto> input = List.of(
                WorkflowMappingDto.builder().internalState("INVALID_STATE").externalStatus("Bad").build()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.saveMappings(projectId, input));

        assertTrue(ex.getMessage().contains("INVALID_STATE"));
        verify(mappingRepository, never()).deleteByProjectId(any());
    }

    @Test
    void should_setTenantId_fromProject_when_saving() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        doNothing().when(mappingRepository).deleteByProjectId(projectId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProjectWorkflowMapping>> captor = ArgumentCaptor.forClass(List.class);

        when(mappingRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.saveMappings(projectId, List.of(
                WorkflowMappingDto.builder().internalState("QA").externalStatus("Testing").build()
        ));

        List<ProjectWorkflowMapping> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(tenantId, saved.get(0).getTenantId());
        assertEquals(projectId, saved.get(0).getProjectId());
        assertEquals("QA", saved.get(0).getInternalState());
        assertEquals("Testing", saved.get(0).getExternalStatus());
    }

    @Test
    void should_returnAllTaskStates() {
        List<String> states = service.getAvailableInternalStates();

        assertNotNull(states);
        assertEquals(8, states.size());
        assertTrue(states.contains("BACKLOG"));
        assertTrue(states.contains("PRIORITIZED"));
        assertTrue(states.contains("PLANNING"));
        assertTrue(states.contains("PROPOSE_CODE"));
        assertTrue(states.contains("REVIEW"));
        assertTrue(states.contains("QA"));
        assertTrue(states.contains("MERGE"));
        assertTrue(states.contains("DONE"));
    }

    @Test
    void should_acceptAllValidStates() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        doNothing().when(mappingRepository).deleteByProjectId(projectId);
        when(mappingRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<WorkflowMappingDto> allStates = List.of(
                WorkflowMappingDto.builder().internalState("BACKLOG").externalStatus("Backlog").build(),
                WorkflowMappingDto.builder().internalState("PRIORITIZED").externalStatus("Prioritized").build(),
                WorkflowMappingDto.builder().internalState("PLANNING").externalStatus("Planning").build(),
                WorkflowMappingDto.builder().internalState("PROPOSE_CODE").externalStatus("In Progress").build(),
                WorkflowMappingDto.builder().internalState("REVIEW").externalStatus("In Review").build(),
                WorkflowMappingDto.builder().internalState("QA").externalStatus("Testing").build(),
                WorkflowMappingDto.builder().internalState("MERGE").externalStatus("Ready to Merge").build(),
                WorkflowMappingDto.builder().internalState("DONE").externalStatus("Done").build()
        );

        List<WorkflowMappingDto> result = service.saveMappings(projectId, allStates);

        assertEquals(8, result.size());
    }
}
