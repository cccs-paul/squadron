package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.WorkflowMappingDto;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import com.squadron.orchestrator.engine.TaskState;
import com.squadron.orchestrator.repository.ProjectRepository;
import com.squadron.orchestrator.repository.ProjectWorkflowMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectWorkflowMappingService {

    private static final Logger log = LoggerFactory.getLogger(ProjectWorkflowMappingService.class);

    private static final Set<String> VALID_STATES = Arrays.stream(TaskState.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    private final ProjectWorkflowMappingRepository mappingRepository;
    private final ProjectRepository projectRepository;

    public ProjectWorkflowMappingService(ProjectWorkflowMappingRepository mappingRepository,
                                          ProjectRepository projectRepository) {
        this.mappingRepository = mappingRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Return all workflow step mappings for a given project.
     */
    @Transactional(readOnly = true)
    public List<WorkflowMappingDto> getMappings(UUID projectId) {
        return mappingRepository.findByProjectId(projectId).stream()
                .map(m -> WorkflowMappingDto.builder()
                        .internalState(m.getInternalState())
                        .externalStatus(m.getExternalStatus())
                        .build())
                .toList();
    }

    /**
     * Replace all workflow step mappings for a project.  Any existing
     * mappings are deleted and the provided list is saved in their place.
     *
     * @throws ResourceNotFoundException if the project does not exist
     * @throws IllegalArgumentException  if an internal state value is invalid
     */
    public List<WorkflowMappingDto> saveMappings(UUID projectId, List<WorkflowMappingDto> mappings) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        // Validate internal states
        for (WorkflowMappingDto dto : mappings) {
            if (!VALID_STATES.contains(dto.getInternalState())) {
                throw new IllegalArgumentException(
                        "Invalid internal state: " + dto.getInternalState()
                                + ". Valid states are: " + VALID_STATES);
            }
        }

        // Delete existing and replace
        mappingRepository.deleteByProjectId(projectId);
        mappingRepository.flush();

        List<ProjectWorkflowMapping> entities = mappings.stream()
                .map(dto -> ProjectWorkflowMapping.builder()
                        .tenantId(project.getTenantId())
                        .projectId(projectId)
                        .internalState(dto.getInternalState())
                        .externalStatus(dto.getExternalStatus())
                        .build())
                .toList();

        List<ProjectWorkflowMapping> saved = mappingRepository.saveAll(entities);

        log.info("Saved {} workflow mappings for project {} ({})",
                saved.size(), project.getName(), projectId);

        return saved.stream()
                .map(m -> WorkflowMappingDto.builder()
                        .internalState(m.getInternalState())
                        .externalStatus(m.getExternalStatus())
                        .build())
                .toList();
    }

    /**
     * Return the list of valid internal states that can be mapped.
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableInternalStates() {
        return Arrays.stream(TaskState.values())
                .map(Enum::name)
                .toList();
    }
}
