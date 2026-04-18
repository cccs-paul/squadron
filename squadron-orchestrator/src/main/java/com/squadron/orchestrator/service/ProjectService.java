package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.dto.ProjectSummaryDto;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.repository.ProjectRepository;
import com.squadron.orchestrator.repository.ProjectWorkflowMappingRepository;
import com.squadron.orchestrator.repository.TaskRepository;
import com.squadron.orchestrator.repository.TaskWorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.squadron.common.security.TenantScopedLookup;

@Service
@Transactional
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskWorkflowRepository taskWorkflowRepository;
    private final ProjectWorkflowMappingRepository mappingRepository;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          TaskWorkflowRepository taskWorkflowRepository,
                          ProjectWorkflowMappingRepository mappingRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.taskWorkflowRepository = taskWorkflowRepository;
        this.mappingRepository = mappingRepository;
    }

    public Project createProject(CreateProjectRequest request) {
        log.info("Creating project '{}' for tenant {}", request.getName(), request.getTenantId());

        Project project = Project.builder()
                .tenantId(request.getTenantId())
                .teamId(request.getTeamId())
                .name(request.getName())
                .repoUrl(request.getRepoUrl())
                .defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
                .branchStrategy(request.getBranchStrategy() != null ? request.getBranchStrategy() : "TRUNK_BASED")
                .branchNamingTemplate(request.getBranchNamingTemplate() != null ? request.getBranchNamingTemplate() : "{strategy}/{ticket}-{description}")
                .connectionId(request.getConnectionId())
                .externalProjectId(request.getExternalProjectId())
                .settings(request.getSettings())
                .gitConnectionId(request.getGitConnectionId())
                .cloneUrl(request.getCloneUrl())
                .build();

        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Project getProject(UUID id) {
        return TenantScopedLookup.findByIdScoped(id, projectRepository::findById, projectRepository::findByIdAndTenantId, () -> new ResourceNotFoundException("Project", id));
    }

    @Transactional(readOnly = true)
    public List<Project> listProjectsByTenant(UUID tenantId) {
        return projectRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public Page<Project> listProjectsByTenant(UUID tenantId, Pageable pageable, String search) {
        if (search != null && !search.isBlank()) {
            return projectRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, search.trim(), pageable);
        }
        return projectRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Project> listProjectsByTeam(UUID teamId) {
        return projectRepository.findByTeamId(teamId);
    }

    public Project updateProject(UUID id, CreateProjectRequest request) {
        Project project = getProject(id);

        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getRepoUrl() != null) {
            project.setRepoUrl(request.getRepoUrl());
        }
        if (request.getDefaultBranch() != null) {
            project.setDefaultBranch(request.getDefaultBranch());
        }
        if (request.getBranchStrategy() != null) {
            project.setBranchStrategy(request.getBranchStrategy());
        }
        if (request.getBranchNamingTemplate() != null) {
            project.setBranchNamingTemplate(request.getBranchNamingTemplate());
        }
        if (request.getConnectionId() != null) {
            project.setConnectionId(request.getConnectionId());
        }
        if (request.getExternalProjectId() != null) {
            project.setExternalProjectId(request.getExternalProjectId());
        }
        if (request.getSettings() != null) {
            project.setSettings(request.getSettings());
        }
        if (request.getGitConnectionId() != null) {
            project.setGitConnectionId(request.getGitConnectionId());
        }
        if (request.getCloneUrl() != null) {
            project.setCloneUrl(request.getCloneUrl());
        }

        return projectRepository.save(project);
    }

    public void deleteProject(UUID id) {
        Project project = getProject(id);
        projectRepository.delete(project);
        log.info("Deleted project {} ({})", project.getName(), id);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getProjectSummaries(UUID tenantId) {
        List<Project> projects = projectRepository.findByTenantId(tenantId);
        return projects.stream().map(project -> {
            // Get tasks for this project and resolve their workflow states
            List<Task> tasks = taskRepository.findByProjectId(project.getId());
            List<UUID> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());

            Map<UUID, String> taskStateMap = Map.of();
            if (!taskIds.isEmpty()) {
                List<TaskWorkflow> workflows = taskWorkflowRepository.findByTaskIdIn(taskIds);
                taskStateMap = workflows.stream()
                        .collect(Collectors.toMap(TaskWorkflow::getTaskId, TaskWorkflow::getCurrentState));
            }

            // Count tasks by state
            Map<UUID, String> finalTaskStateMap = taskStateMap;
            Map<String, Long> taskCountsByState = tasks.stream()
                    .map(t -> finalTaskStateMap.getOrDefault(t.getId(), "BACKLOG"))
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            long totalTasks = tasks.size();
            long activeTasks = tasks.stream()
                    .filter(t -> {
                        String state = finalTaskStateMap.getOrDefault(t.getId(), "BACKLOG");
                        return !"DONE".equals(state) && !"BACKLOG".equals(state);
                    })
                    .count();

            // Check workflow mappings
            List<ProjectWorkflowMapping> mappings = mappingRepository.findByProjectId(project.getId());

            return ProjectSummaryDto.builder()
                    .id(project.getId())
                    .tenantId(project.getTenantId())
                    .teamId(project.getTeamId())
                    .name(project.getName())
                    .description(project.getDescription())
                    .repositoryUrl(project.getRepoUrl())
                    .defaultBranch(project.getDefaultBranch())
                    .branchNamingTemplate(project.getBranchNamingTemplate())
                    .connectionId(project.getConnectionId())
                    .externalProjectId(project.getExternalProjectId())
                    .gitConnectionId(project.getGitConnectionId())
                    .totalTasks(totalTasks)
                    .activeTasks(activeTasks)
                    .taskCountsByState(taskCountsByState)
                    .workflowMappingsConfigured(!mappings.isEmpty())
                    .workflowMappingCount(mappings.size())
                    .createdAt(project.getCreatedAt() != null ? project.getCreatedAt().toString() : null)
                    .updatedAt(project.getUpdatedAt() != null ? project.getUpdatedAt().toString() : null)
                    .build();
        }).collect(Collectors.toList());
    }
}
