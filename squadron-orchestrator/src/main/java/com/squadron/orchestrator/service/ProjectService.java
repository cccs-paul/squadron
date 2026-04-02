package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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
                .build();

        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Project getProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Transactional(readOnly = true)
    public List<Project> listProjectsByTenant(UUID tenantId) {
        return projectRepository.findByTenantId(tenantId);
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

        return projectRepository.save(project);
    }

    public void deleteProject(UUID id) {
        Project project = getProject(id);
        projectRepository.delete(project);
        log.info("Deleted project {} ({})", project.getName(), id);
    }
}
