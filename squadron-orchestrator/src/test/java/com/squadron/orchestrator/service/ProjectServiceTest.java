package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository);
    }

    @Test
    void should_createProject_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .defaultBranch("develop")
                .branchStrategy("GIT_FLOW")
                .connectionId(connectionId)
                .externalProjectId("EXT-1")
                .settings("{}")
                .build();

        Project savedProject = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .repoUrl("https://github.com/test/repo")
                .defaultBranch("develop")
                .branchStrategy("GIT_FLOW")
                .connectionId(connectionId)
                .externalProjectId("EXT-1")
                .settings("{}")
                .build();

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        Project result = projectService.createProject(request);

        assertNotNull(result);
        assertEquals("Test Project", result.getName());
        assertEquals(tenantId, result.getTenantId());
        assertEquals("develop", result.getDefaultBranch());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void should_createProject_withDefaultBranch_when_branchIsNull() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Test")
                .build();

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = projectService.createProject(request);

        assertEquals("main", result.getDefaultBranch());
        assertEquals("TRUNK_BASED", result.getBranchStrategy());
    }

    @Test
    void should_createProject_withDefaultBranchStrategy_when_strategyIsNull() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Test")
                .defaultBranch("main")
                .build();

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = projectService.createProject(request);

        assertEquals("TRUNK_BASED", result.getBranchStrategy());
    }

    @Test
    void should_getProject_when_exists() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Found Project")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        Project result = projectService.getProject(projectId);

        assertEquals(projectId, result.getId());
        assertEquals("Found Project", result.getName());
    }

    @Test
    void should_throwNotFound_when_projectMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getProject(projectId));
    }

    @Test
    void should_listProjectsByTenant() {
        UUID tenantId = UUID.randomUUID();
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("P1").build(),
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("P2").build()
        );

        when(projectRepository.findByTenantId(tenantId)).thenReturn(projects);

        List<Project> result = projectService.listProjectsByTenant(tenantId);

        assertEquals(2, result.size());
    }

    @Test
    void should_listProjectsByTeam() {
        UUID teamId = UUID.randomUUID();
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).teamId(teamId).name("P1").build()
        );

        when(projectRepository.findByTeamId(teamId)).thenReturn(projects);

        List<Project> result = projectService.listProjectsByTeam(teamId);

        assertEquals(1, result.size());
    }

    @Test
    void should_updateProject_when_allFieldsProvided() {
        UUID projectId = UUID.randomUUID();
        Project existing = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Old Name")
                .repoUrl("old-url")
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .settings("{}")
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("New Name")
                .repoUrl("new-url")
                .defaultBranch("develop")
                .branchStrategy("GIT_FLOW")
                .connectionId(UUID.randomUUID())
                .externalProjectId("EXT-NEW")
                .settings("{\"new\":true}")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenReturn(existing);

        Project result = projectService.updateProject(projectId, request);

        assertEquals("New Name", existing.getName());
        assertEquals("new-url", existing.getRepoUrl());
        assertEquals("develop", existing.getDefaultBranch());
        assertEquals("GIT_FLOW", existing.getBranchStrategy());
        assertEquals("EXT-NEW", existing.getExternalProjectId());
        assertEquals("{\"new\":true}", existing.getSettings());
    }

    @Test
    void should_updateProject_when_onlyNameProvided() {
        UUID projectId = UUID.randomUUID();
        Project existing = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Old Name")
                .repoUrl("old-url")
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("New Name")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenReturn(existing);

        projectService.updateProject(projectId, request);

        assertEquals("New Name", existing.getName());
        assertEquals("old-url", existing.getRepoUrl()); // unchanged
        assertEquals("main", existing.getDefaultBranch()); // unchanged
    }

    @Test
    void should_throwNotFound_when_updatingMissingProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        CreateProjectRequest request = CreateProjectRequest.builder().name("N").build();

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.updateProject(projectId, request));
    }

    @Test
    void should_deleteProject_when_exists() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("To Delete")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        projectService.deleteProject(projectId);

        verify(projectRepository).delete(project);
    }

    @Test
    void should_throwNotFound_when_deletingMissingProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.deleteProject(projectId));
    }
}
