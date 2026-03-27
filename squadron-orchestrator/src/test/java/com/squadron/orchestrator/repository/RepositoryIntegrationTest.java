package com.squadron.orchestrator.repository;

import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
@Testcontainers
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan("com.squadron.orchestrator.entity")
    @EnableJpaRepositories("com.squadron.orchestrator.repository")
    static class TestConfig {
    }

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_orchestrator_test");

    @BeforeAll
    static void startContainer() {
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskWorkflowRepository taskWorkflowRepository;

    @Autowired
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    // ========================================================================
    // Helper methods
    // ========================================================================

    private Project createAndSaveProject(UUID tenantId, UUID teamId, String name) {
        Project project = Project.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name(name)
                .repoUrl("https://github.com/test/" + name)
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .build();
        return projectRepository.save(project);
    }

    private Task createAndSaveTask(UUID tenantId, UUID teamId, UUID projectId, String title) {
        Task task = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(projectId)
                .title(title)
                .description("Description for " + title)
                .priority("MEDIUM")
                .build();
        return taskRepository.save(task);
    }

    private TaskWorkflow createAndSaveTaskWorkflow(UUID tenantId, UUID taskId,
                                                    String currentState, String previousState) {
        TaskWorkflow workflow = TaskWorkflow.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .currentState(currentState)
                .previousState(previousState)
                .transitionAt(Instant.now())
                .transitionedBy(UUID.randomUUID())
                .build();
        return taskWorkflowRepository.save(workflow);
    }

    // ========================================================================
    // ProjectRepository tests
    // ========================================================================

    @Test
    void should_saveAndFindProjectById() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Project saved = createAndSaveProject(tenantId, teamId, "test-project");

        Optional<Project> found = projectRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("test-project");
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getTeamId()).isEqualTo(teamId);
        assertThat(found.get().getDefaultBranch()).isEqualTo("main");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findProjectsByTenantId() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        createAndSaveProject(tenantId, teamId, "project-a");
        createAndSaveProject(tenantId, teamId, "project-b");
        createAndSaveProject(otherTenantId, teamId, "project-other");

        List<Project> results = projectRepository.findByTenantId(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Project::getName)
                .containsExactlyInAnyOrder("project-a", "project-b");
    }

    @Test
    void should_findProjectsByTeamId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();

        createAndSaveProject(tenantId, teamA, "team-a-project");
        createAndSaveProject(tenantId, teamB, "team-b-project");

        List<Project> results = projectRepository.findByTeamId(teamA);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("team-a-project");
    }

    @Test
    void should_updateProject() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Project saved = createAndSaveProject(tenantId, teamId, "original-name");
        Instant originalUpdatedAt = saved.getUpdatedAt();

        saved.setName("updated-name");
        saved.setBranchStrategy("GIT_FLOW");
        Project updated = projectRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("updated-name");
        assertThat(updated.getBranchStrategy()).isEqualTo("GIT_FLOW");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void should_deleteProject() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Project saved = createAndSaveProject(tenantId, teamId, "to-delete");

        projectRepository.deleteById(saved.getId());

        assertThat(projectRepository.findById(saved.getId())).isEmpty();
    }

    // ========================================================================
    // TaskRepository tests
    // ========================================================================

    @Test
    void should_saveAndFindTaskById() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "task-project");

        Task saved = createAndSaveTask(tenantId, teamId, project.getId(), "Implement feature");

        Optional<Task> found = taskRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Implement feature");
        assertThat(found.get().getDescription()).isEqualTo("Description for Implement feature");
        assertThat(found.get().getPriority()).isEqualTo("MEDIUM");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void should_findTasksByProjectId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project projectA = createAndSaveProject(tenantId, teamId, "proj-a");
        Project projectB = createAndSaveProject(tenantId, teamId, "proj-b");

        createAndSaveTask(tenantId, teamId, projectA.getId(), "Task in A-1");
        createAndSaveTask(tenantId, teamId, projectA.getId(), "Task in A-2");
        createAndSaveTask(tenantId, teamId, projectB.getId(), "Task in B");

        List<Task> results = taskRepository.findByProjectId(projectA.getId());

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Task in A-1", "Task in A-2");
    }

    @Test
    void should_findTasksByTenantId() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project projectA = createAndSaveProject(tenantA, teamId, "proj-tenant-a");
        Project projectB = createAndSaveProject(tenantB, teamId, "proj-tenant-b");

        createAndSaveTask(tenantA, teamId, projectA.getId(), "Tenant A task");
        createAndSaveTask(tenantB, teamId, projectB.getId(), "Tenant B task");

        List<Task> results = taskRepository.findByTenantId(tenantA);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Tenant A task");
    }

    @Test
    void should_findTaskByExternalId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "external-proj");

        Task task = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(project.getId())
                .title("JIRA task")
                .externalId("JIRA-1234")
                .externalUrl("https://jira.example.com/JIRA-1234")
                .build();
        taskRepository.save(task);

        Optional<Task> found = taskRepository.findByExternalId("JIRA-1234");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("JIRA task");
        assertThat(found.get().getExternalUrl()).isEqualTo("https://jira.example.com/JIRA-1234");
    }

    @Test
    void should_findTasksByAssigneeId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID assignee = UUID.randomUUID();
        UUID otherAssignee = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "assignee-proj");

        Task task1 = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(project.getId())
                .title("Assigned task 1")
                .assigneeId(assignee)
                .build();
        Task task2 = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(project.getId())
                .title("Assigned task 2")
                .assigneeId(assignee)
                .build();
        Task task3 = Task.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(project.getId())
                .title("Other assignee task")
                .assigneeId(otherAssignee)
                .build();
        taskRepository.saveAll(List.of(task1, task2, task3));

        List<Task> results = taskRepository.findByAssigneeId(assignee);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Assigned task 1", "Assigned task 2");
    }

    @Test
    void should_findTasksByTeamId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        Project projectA = createAndSaveProject(tenantId, teamA, "team-a-proj");
        Project projectB = createAndSaveProject(tenantId, teamB, "team-b-proj");

        createAndSaveTask(tenantId, teamA, projectA.getId(), "Team A task");
        createAndSaveTask(tenantId, teamB, projectB.getId(), "Team B task");

        List<Task> results = taskRepository.findByTeamId(teamA);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Team A task");
    }

    @Test
    void should_returnEmptyWhenExternalIdNotFound() {
        Optional<Task> result = taskRepository.findByExternalId("NONEXISTENT-999");

        assertThat(result).isEmpty();
    }

    // ========================================================================
    // TaskWorkflowRepository tests
    // ========================================================================

    @Test
    void should_saveAndFindTaskWorkflowByTaskId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "wf-proj");
        Task task = createAndSaveTask(tenantId, teamId, project.getId(), "WF task");

        TaskWorkflow saved = createAndSaveTaskWorkflow(tenantId, task.getId(), "IN_PROGRESS", "OPEN");

        Optional<TaskWorkflow> found = taskWorkflowRepository.findByTaskId(task.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCurrentState()).isEqualTo("IN_PROGRESS");
        assertThat(found.get().getPreviousState()).isEqualTo("OPEN");
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void should_findTaskWorkflowsByCurrentState() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "state-proj");
        Task task1 = createAndSaveTask(tenantId, teamId, project.getId(), "Task 1");
        Task task2 = createAndSaveTask(tenantId, teamId, project.getId(), "Task 2");
        Task task3 = createAndSaveTask(tenantId, teamId, project.getId(), "Task 3");

        createAndSaveTaskWorkflow(tenantId, task1.getId(), "IN_PROGRESS", null);
        createAndSaveTaskWorkflow(tenantId, task2.getId(), "IN_PROGRESS", "OPEN");
        createAndSaveTaskWorkflow(tenantId, task3.getId(), "DONE", "IN_PROGRESS");

        List<TaskWorkflow> results = taskWorkflowRepository.findByCurrentState("IN_PROGRESS");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(tw -> "IN_PROGRESS".equals(tw.getCurrentState()));
    }

    @Test
    void should_findTaskWorkflowsByTenantIdAndCurrentState() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project projectA = createAndSaveProject(tenantA, teamId, "tenant-a-wf");
        Project projectB = createAndSaveProject(tenantB, teamId, "tenant-b-wf");
        Task taskA = createAndSaveTask(tenantA, teamId, projectA.getId(), "Tenant A wf task");
        Task taskB = createAndSaveTask(tenantB, teamId, projectB.getId(), "Tenant B wf task");

        createAndSaveTaskWorkflow(tenantA, taskA.getId(), "OPEN", null);
        createAndSaveTaskWorkflow(tenantB, taskB.getId(), "OPEN", null);

        List<TaskWorkflow> results = taskWorkflowRepository
                .findByTenantIdAndCurrentState(tenantA, "OPEN");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(tenantA);
    }

    @Test
    void should_findTaskWorkflowsByTenantId() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "tenant-wf-proj");
        Project otherProject = createAndSaveProject(otherTenantId, teamId, "other-tenant-wf-proj");
        Task task1 = createAndSaveTask(tenantId, teamId, project.getId(), "TW task 1");
        Task task2 = createAndSaveTask(tenantId, teamId, project.getId(), "TW task 2");
        Task task3 = createAndSaveTask(otherTenantId, teamId, otherProject.getId(), "Other TW task");

        createAndSaveTaskWorkflow(tenantId, task1.getId(), "OPEN", null);
        createAndSaveTaskWorkflow(tenantId, task2.getId(), "IN_PROGRESS", "OPEN");
        createAndSaveTaskWorkflow(otherTenantId, task3.getId(), "OPEN", null);

        List<TaskWorkflow> results = taskWorkflowRepository.findByTenantId(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(tw -> tenantId.equals(tw.getTenantId()));
    }

    @Test
    void should_findTaskWorkflowByTaskIdForUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "lock-proj");
        Task task = createAndSaveTask(tenantId, teamId, project.getId(), "Lock task");

        createAndSaveTaskWorkflow(tenantId, task.getId(), "OPEN", null);

        Optional<TaskWorkflow> found = taskWorkflowRepository.findByTaskIdForUpdate(task.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo(task.getId());
        assertThat(found.get().getCurrentState()).isEqualTo("OPEN");
    }

    // ========================================================================
    // TaskStateHistoryRepository tests
    // ========================================================================

    @Test
    void should_saveAndFindTaskStateHistoryByWorkflowId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "history-proj");
        Task task = createAndSaveTask(tenantId, teamId, project.getId(), "History task");
        TaskWorkflow workflow = createAndSaveTaskWorkflow(tenantId, task.getId(), "IN_PROGRESS", "OPEN");

        TaskStateHistory history = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState("OPEN")
                .toState("IN_PROGRESS")
                .triggeredBy(triggeredBy)
                .reason("Starting work")
                .build();
        taskStateHistoryRepository.save(history);

        List<TaskStateHistory> results = taskStateHistoryRepository
                .findByTaskWorkflowIdOrderByCreatedAtDesc(workflow.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFromState()).isEqualTo("OPEN");
        assertThat(results.get(0).getToState()).isEqualTo("IN_PROGRESS");
        assertThat(results.get(0).getReason()).isEqualTo("Starting work");
        assertThat(results.get(0).getTriggeredBy()).isEqualTo(triggeredBy);
        assertThat(results.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void should_returnHistoryOrderedByCreatedAtDescending() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "order-proj");
        Task task = createAndSaveTask(tenantId, teamId, project.getId(), "Order task");
        TaskWorkflow workflow = createAndSaveTaskWorkflow(tenantId, task.getId(), "DONE", "IN_PROGRESS");

        TaskStateHistory first = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState(null)
                .toState("OPEN")
                .triggeredBy(triggeredBy)
                .reason("Created")
                .build();
        taskStateHistoryRepository.saveAndFlush(first);

        // Small delay to ensure distinct timestamps
        Thread.sleep(50);

        TaskStateHistory second = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState("OPEN")
                .toState("IN_PROGRESS")
                .triggeredBy(triggeredBy)
                .reason("Started work")
                .build();
        taskStateHistoryRepository.saveAndFlush(second);

        Thread.sleep(50);

        TaskStateHistory third = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState("IN_PROGRESS")
                .toState("DONE")
                .triggeredBy(triggeredBy)
                .reason("Completed")
                .build();
        taskStateHistoryRepository.saveAndFlush(third);

        List<TaskStateHistory> results = taskStateHistoryRepository
                .findByTaskWorkflowIdOrderByCreatedAtDesc(workflow.getId());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getToState()).isEqualTo("DONE");
        assertThat(results.get(1).getToState()).isEqualTo("IN_PROGRESS");
        assertThat(results.get(2).getToState()).isEqualTo("OPEN");
    }

    @Test
    void should_returnEmptyHistoryForNonexistentWorkflow() {
        UUID randomWorkflowId = UUID.randomUUID();

        List<TaskStateHistory> results = taskStateHistoryRepository
                .findByTaskWorkflowIdOrderByCreatedAtDesc(randomWorkflowId);

        assertThat(results).isEmpty();
    }

    @Test
    void should_saveHistoryWithNullFromState() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "null-from-proj");
        Task task = createAndSaveTask(tenantId, teamId, project.getId(), "Initial task");
        TaskWorkflow workflow = createAndSaveTaskWorkflow(tenantId, task.getId(), "OPEN", null);

        TaskStateHistory history = TaskStateHistory.builder()
                .taskWorkflowId(workflow.getId())
                .fromState(null)
                .toState("OPEN")
                .triggeredBy(UUID.randomUUID())
                .reason("Initial creation")
                .build();
        TaskStateHistory saved = taskStateHistoryRepository.save(history);

        Optional<TaskStateHistory> found = taskStateHistoryRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFromState()).isNull();
        assertThat(found.get().getToState()).isEqualTo("OPEN");
    }

    @Test
    void should_isolateHistoryBetweenWorkflows() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();
        Project project = createAndSaveProject(tenantId, teamId, "isolate-proj");
        Task task1 = createAndSaveTask(tenantId, teamId, project.getId(), "Isolate task 1");
        Task task2 = createAndSaveTask(tenantId, teamId, project.getId(), "Isolate task 2");
        TaskWorkflow workflow1 = createAndSaveTaskWorkflow(tenantId, task1.getId(), "OPEN", null);
        TaskWorkflow workflow2 = createAndSaveTaskWorkflow(tenantId, task2.getId(), "IN_PROGRESS", "OPEN");

        taskStateHistoryRepository.save(TaskStateHistory.builder()
                .taskWorkflowId(workflow1.getId())
                .fromState(null)
                .toState("OPEN")
                .triggeredBy(triggeredBy)
                .build());
        taskStateHistoryRepository.save(TaskStateHistory.builder()
                .taskWorkflowId(workflow2.getId())
                .fromState("OPEN")
                .toState("IN_PROGRESS")
                .triggeredBy(triggeredBy)
                .build());
        taskStateHistoryRepository.save(TaskStateHistory.builder()
                .taskWorkflowId(workflow2.getId())
                .fromState("IN_PROGRESS")
                .toState("DONE")
                .triggeredBy(triggeredBy)
                .build());

        List<TaskStateHistory> wf1History = taskStateHistoryRepository
                .findByTaskWorkflowIdOrderByCreatedAtDesc(workflow1.getId());
        List<TaskStateHistory> wf2History = taskStateHistoryRepository
                .findByTaskWorkflowIdOrderByCreatedAtDesc(workflow2.getId());

        assertThat(wf1History).hasSize(1);
        assertThat(wf2History).hasSize(2);
    }

    // ========================================================================
    // WorkflowDefinitionRepository tests
    // ========================================================================

    @Test
    void should_saveAndFindWorkflowDefinitionById() {
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition def = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .name("Default Workflow")
                .states("[\"OPEN\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"IN_PROGRESS\"],\"IN_PROGRESS\":[\"DONE\"]}")
                .hooks("{}")
                .active(true)
                .build();
        WorkflowDefinition saved = workflowDefinitionRepository.save(def);

        Optional<WorkflowDefinition> found = workflowDefinitionRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Default Workflow");
        assertThat(found.get().getStates()).contains("OPEN", "IN_PROGRESS", "DONE");
        assertThat(found.get().getActive()).isTrue();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void should_findActiveWorkflowByTenantIdAndTeamId() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        WorkflowDefinition activeDef = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Active Team Workflow")
                .states("[\"OPEN\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"IN_PROGRESS\"],\"IN_PROGRESS\":[\"DONE\"]}")
                .hooks("{}")
                .active(true)
                .build();
        WorkflowDefinition inactiveDef = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Inactive Team Workflow")
                .states("[\"OPEN\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"DONE\"]}")
                .hooks("{}")
                .active(false)
                .build();
        workflowDefinitionRepository.saveAll(List.of(activeDef, inactiveDef));

        Optional<WorkflowDefinition> found = workflowDefinitionRepository
                .findByTenantIdAndTeamIdAndActiveTrue(tenantId, teamId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Active Team Workflow");
        assertThat(found.get().getActive()).isTrue();
    }

    @Test
    void should_findActiveTenantWideWorkflowWithNullTeamId() {
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition tenantWide = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .teamId(null)
                .name("Tenant-wide Workflow")
                .states("[\"OPEN\",\"IN_PROGRESS\",\"REVIEW\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"IN_PROGRESS\"],\"IN_PROGRESS\":[\"REVIEW\"],\"REVIEW\":[\"DONE\"]}")
                .hooks("{}")
                .active(true)
                .build();
        workflowDefinitionRepository.save(tenantWide);

        Optional<WorkflowDefinition> found = workflowDefinitionRepository
                .findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tenant-wide Workflow");
        assertThat(found.get().getTeamId()).isNull();
    }

    @Test
    void should_notFindTenantWideWorkflowWhenInactive() {
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition inactive = WorkflowDefinition.builder()
                .tenantId(tenantId)
                .teamId(null)
                .name("Inactive Tenant Workflow")
                .states("[\"OPEN\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"DONE\"]}")
                .hooks("{}")
                .active(false)
                .build();
        workflowDefinitionRepository.save(inactive);

        Optional<WorkflowDefinition> found = workflowDefinitionRepository
                .findByTenantIdAndTeamIdIsNullAndActiveTrue(tenantId);

        assertThat(found).isEmpty();
    }

    @Test
    void should_findAllWorkflowDefinitionsByTenantId() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .tenantId(tenantA)
                .name("Tenant A Def 1")
                .states("[\"OPEN\"]")
                .transitions("{}")
                .hooks("{}")
                .active(true)
                .build());
        workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .tenantId(tenantA)
                .name("Tenant A Def 2")
                .states("[\"OPEN\"]")
                .transitions("{}")
                .hooks("{}")
                .active(false)
                .build());
        workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .tenantId(tenantB)
                .name("Tenant B Def")
                .states("[\"OPEN\"]")
                .transitions("{}")
                .hooks("{}")
                .active(true)
                .build());

        List<WorkflowDefinition> results = workflowDefinitionRepository.findByTenantId(tenantA);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(WorkflowDefinition::getName)
                .containsExactlyInAnyOrder("Tenant A Def 1", "Tenant A Def 2");
    }
}
