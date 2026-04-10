package com.squadron.orchestrator.service;

import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.repository.ProjectRepository;
import com.squadron.orchestrator.repository.ProjectWorkflowMappingRepository;
import com.squadron.orchestrator.repository.TaskRepository;
import com.squadron.orchestrator.repository.TaskWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.squadron.orchestrator.dto.ProjectSummaryDto;
import com.squadron.orchestrator.entity.ProjectWorkflowMapping;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskWorkflow;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskWorkflowRepository taskWorkflowRepository;

    @Mock
    private ProjectWorkflowMappingRepository mappingRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, taskRepository,
                taskWorkflowRepository, mappingRepository);
    }

    @Test
    void should_createProject_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID gitConnectionId = UUID.randomUUID();

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
                .gitConnectionId(gitConnectionId)
                .cloneUrl("git@github.com:test/repo.git")
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
                .gitConnectionId(gitConnectionId)
                .cloneUrl("git@github.com:test/repo.git")
                .build();

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        Project result = projectService.createProject(request);

        assertNotNull(result);
        assertEquals("Test Project", result.getName());
        assertEquals(tenantId, result.getTenantId());
        assertEquals("develop", result.getDefaultBranch());
        assertEquals(gitConnectionId, result.getGitConnectionId());
        assertEquals("git@github.com:test/repo.git", result.getCloneUrl());
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
        UUID gitConnectionId = UUID.randomUUID();
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
                .gitConnectionId(gitConnectionId)
                .cloneUrl("git@github.com:new/repo.git")
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
        assertEquals(gitConnectionId, existing.getGitConnectionId());
        assertEquals("git@github.com:new/repo.git", existing.getCloneUrl());
    }

    @Test
    void should_updateProject_when_onlyNameProvided() {
        UUID projectId = UUID.randomUUID();
        UUID existingGitConnectionId = UUID.randomUUID();
        Project existing = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Old Name")
                .repoUrl("old-url")
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .gitConnectionId(existingGitConnectionId)
                .cloneUrl("git@github.com:old/repo.git")
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
        assertEquals(existingGitConnectionId, existing.getGitConnectionId()); // unchanged
        assertEquals("git@github.com:old/repo.git", existing.getCloneUrl()); // unchanged
    }

    @Test
    void should_throwNotFound_when_updatingMissingProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        CreateProjectRequest request = CreateProjectRequest.builder().name("N").build();

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.updateProject(projectId, request));
    }

    // --- branchNamingTemplate ---

    @Test
    void should_createProject_withBranchNamingTemplate_when_provided() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Template Project")
                .branchNamingTemplate("feature/{ticket}-{description}")
                .build();

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = projectService.createProject(request);

        assertEquals("feature/{ticket}-{description}", result.getBranchNamingTemplate());
    }

    @Test
    void should_createProject_withDefaultBranchNamingTemplate_when_templateIsNull() {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Default Template")
                .branchNamingTemplate(null)
                .build();

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = projectService.createProject(request);

        assertEquals("{strategy}/{ticket}-{description}", result.getBranchNamingTemplate());
    }

    @Test
    void should_updateProject_withBranchNamingTemplate_when_provided() {
        UUID projectId = UUID.randomUUID();
        Project existing = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Old")
                .branchNamingTemplate("{strategy}/{ticket}-{description}")
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .branchNamingTemplate("bugfix/{ticket}")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenReturn(existing);

        projectService.updateProject(projectId, request);

        assertEquals("bugfix/{ticket}", existing.getBranchNamingTemplate());
    }

    @Test
    void should_notUpdateBranchNamingTemplate_when_templateIsNull() {
        UUID projectId = UUID.randomUUID();
        Project existing = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Existing")
                .branchNamingTemplate("{strategy}/{ticket}-{description}")
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .name("Updated Name")
                .branchNamingTemplate(null)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenReturn(existing);

        projectService.updateProject(projectId, request);

        assertEquals("{strategy}/{ticket}-{description}", existing.getBranchNamingTemplate());
        assertEquals("Updated Name", existing.getName());
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

    // --- Paginated listing ---

    @Test
    void should_listProjectsByTenantPaginated_when_noSearch() {
        UUID tenantId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("P1").build(),
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("P2").build()
        );
        Page<Project> page = new PageImpl<>(projects, pageable, 2);

        when(projectRepository.findByTenantId(eq(tenantId), eq(pageable))).thenReturn(page);

        Page<Project> result = projectService.listProjectsByTenant(tenantId, pageable, null);

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(projectRepository).findByTenantId(tenantId, pageable);
    }

    @Test
    void should_listProjectsByTenantPaginated_when_searchProvided() {
        UUID tenantId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("Squadron API").build()
        );
        Page<Project> page = new PageImpl<>(projects, pageable, 1);

        when(projectRepository.findByTenantIdAndNameContainingIgnoreCase(eq(tenantId), eq("squadron"), eq(pageable)))
                .thenReturn(page);

        Page<Project> result = projectService.listProjectsByTenant(tenantId, pageable, "squadron");

        assertEquals(1, result.getTotalElements());
        verify(projectRepository).findByTenantIdAndNameContainingIgnoreCase(tenantId, "squadron", pageable);
    }

    @Test
    void should_listProjectsByTenantPaginated_when_searchIsBlank() {
        UUID tenantId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Project> page = new PageImpl<>(List.of(), pageable, 0);

        when(projectRepository.findByTenantId(eq(tenantId), eq(pageable))).thenReturn(page);

        Page<Project> result = projectService.listProjectsByTenant(tenantId, pageable, "   ");

        assertEquals(0, result.getTotalElements());
        verify(projectRepository).findByTenantId(tenantId, pageable);
    }

    // --- Project summaries ---

    @Test
    void should_getProjectSummaries_when_projectsExist() {
        UUID tenantId = UUID.randomUUID();

        Project project1 = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Project Alpha")
                .build();
        Project project2 = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Project Beta")
                .build();

        when(projectRepository.findByTenantId(tenantId)).thenReturn(List.of(project1, project2));

        // Project 1: 3 tasks
        Task task1 = Task.builder().id(UUID.randomUUID()).projectId(project1.getId()).title("T1").build();
        Task task2 = Task.builder().id(UUID.randomUUID()).projectId(project1.getId()).title("T2").build();
        Task task3 = Task.builder().id(UUID.randomUUID()).projectId(project1.getId()).title("T3").build();
        when(taskRepository.findByProjectId(project1.getId())).thenReturn(List.of(task1, task2, task3));

        List<UUID> taskIds = List.of(task1.getId(), task2.getId(), task3.getId());
        // task1 -> PLANNING, task2 -> REVIEW, task3 has no workflow (defaults to BACKLOG)
        TaskWorkflow wf1 = TaskWorkflow.builder().taskId(task1.getId()).currentState("PLANNING")
                .tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build();
        TaskWorkflow wf2 = TaskWorkflow.builder().taskId(task2.getId()).currentState("REVIEW")
                .tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build();
        when(taskWorkflowRepository.findByTaskIdIn(taskIds)).thenReturn(List.of(wf1, wf2));

        // 2 workflow mappings for project 1
        ProjectWorkflowMapping mapping1 = ProjectWorkflowMapping.builder()
                .id(UUID.randomUUID()).projectId(project1.getId()).tenantId(tenantId)
                .internalState("PLANNING").externalStatus("In Progress").build();
        ProjectWorkflowMapping mapping2 = ProjectWorkflowMapping.builder()
                .id(UUID.randomUUID()).projectId(project1.getId()).tenantId(tenantId)
                .internalState("REVIEW").externalStatus("Code Review").build();
        when(mappingRepository.findByProjectId(project1.getId())).thenReturn(List.of(mapping1, mapping2));

        // Project 2: no tasks, no workflows, no mappings
        when(taskRepository.findByProjectId(project2.getId())).thenReturn(Collections.emptyList());
        when(mappingRepository.findByProjectId(project2.getId())).thenReturn(Collections.emptyList());

        List<ProjectSummaryDto> result = projectService.getProjectSummaries(tenantId);

        assertEquals(2, result.size());

        // Summary 1 — Project Alpha
        ProjectSummaryDto summary1 = result.get(0);
        assertEquals("Project Alpha", summary1.getName());
        assertEquals(3, summary1.getTotalTasks());
        assertEquals(2, summary1.getActiveTasks()); // PLANNING + REVIEW are active; BACKLOG is not
        assertNotNull(summary1.getTaskCountsByState());
        assertTrue(summary1.getTaskCountsByState().containsKey("PLANNING"));
        assertTrue(summary1.isWorkflowMappingsConfigured());
        assertEquals(2, summary1.getWorkflowMappingCount());

        // Summary 2 — Project Beta
        ProjectSummaryDto summary2 = result.get(1);
        assertEquals(0, summary2.getTotalTasks());
        assertEquals(0, summary2.getActiveTasks());
        assertFalse(summary2.isWorkflowMappingsConfigured());
    }

    @Test
    void should_getProjectSummaries_when_noProjects() {
        UUID tenantId = UUID.randomUUID();
        when(projectRepository.findByTenantId(tenantId)).thenReturn(Collections.emptyList());

        List<ProjectSummaryDto> result = projectService.getProjectSummaries(tenantId);

        assertTrue(result.isEmpty());
    }

    @Test
    void should_getProjectSummaries_when_allTasksAreDone() {
        UUID tenantId = UUID.randomUUID();

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Done Project")
                .build();
        when(projectRepository.findByTenantId(tenantId)).thenReturn(List.of(project));

        Task task1 = Task.builder().id(UUID.randomUUID()).projectId(project.getId()).title("T1").build();
        Task task2 = Task.builder().id(UUID.randomUUID()).projectId(project.getId()).title("T2").build();
        when(taskRepository.findByProjectId(project.getId())).thenReturn(List.of(task1, task2));

        List<UUID> taskIds = List.of(task1.getId(), task2.getId());
        TaskWorkflow wf1 = TaskWorkflow.builder().taskId(task1.getId()).currentState("DONE")
                .tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build();
        TaskWorkflow wf2 = TaskWorkflow.builder().taskId(task2.getId()).currentState("DONE")
                .tenantId(tenantId).transitionAt(Instant.now()).transitionedBy(UUID.randomUUID()).build();
        when(taskWorkflowRepository.findByTaskIdIn(taskIds)).thenReturn(List.of(wf1, wf2));

        when(mappingRepository.findByProjectId(project.getId())).thenReturn(Collections.emptyList());

        List<ProjectSummaryDto> result = projectService.getProjectSummaries(tenantId);

        assertEquals(1, result.size());
        ProjectSummaryDto summary = result.get(0);
        assertEquals(2, summary.getTotalTasks());
        assertEquals(0, summary.getActiveTasks());
    }
}
