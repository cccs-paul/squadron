package com.squadron.workspace.repository;

import com.squadron.workspace.entity.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_workspace_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Workspace createWorkspace(UUID tenantId, UUID taskId, UUID userId, String status) {
        Workspace workspace = Workspace.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .providerType("DOCKER")
                .repoUrl("https://github.com/org/repo.git")
                .branch("feature/test")
                .baseImage("ubuntu:22.04")
                .status(status)
                .build();
        return entityManager.persistFlushFind(workspace);
    }

    private Workspace createWorkspace(UUID tenantId, UUID taskId, UUID userId) {
        return createWorkspace(tenantId, taskId, userId, "CREATING");
    }

    // =========================================================================
    // WorkspaceRepository Tests
    // =========================================================================

    @Test
    void should_saveWorkspace_when_validEntity() {
        Workspace workspace = Workspace.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("KUBERNETES")
                .repoUrl("https://github.com/org/repo.git")
                .branch("main")
                .baseImage("node:20")
                .resourceLimits("{\"cpu\": \"2\", \"memory\": \"4Gi\"}")
                .build();

        Workspace saved = workspaceRepository.save(workspace);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProviderType()).isEqualTo("KUBERNETES");
        assertThat(saved.getRepoUrl()).isEqualTo("https://github.com/org/repo.git");
        assertThat(saved.getBranch()).isEqualTo("main");
        assertThat(saved.getBaseImage()).isEqualTo("node:20");
        assertThat(saved.getStatus()).isEqualTo("CREATING");
        assertThat(saved.getResourceLimits()).contains("cpu");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getTerminatedAt()).isNull();
    }

    @Test
    void should_findById_when_workspaceExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Workspace workspace = createWorkspace(tenantId, taskId, userId);

        Optional<Workspace> found = workspaceRepository.findById(workspace.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo(taskId);
        assertThat(found.get().getProviderType()).isEqualTo("DOCKER");
    }

    @Test
    void should_returnEmpty_when_workspaceNotFound() {
        Optional<Workspace> found = workspaceRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTaskId_when_workspacesExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createWorkspace(tenantId, taskId, userId, "READY");
        createWorkspace(tenantId, taskId, userId, "TERMINATED");
        createWorkspace(tenantId, UUID.randomUUID(), userId, "READY");

        List<Workspace> results = workspaceRepository.findByTaskId(taskId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(w -> w.getTaskId().equals(taskId));
    }

    @Test
    void should_findByUserId_when_workspacesExist() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        createWorkspace(tenantId, UUID.randomUUID(), userId);
        createWorkspace(tenantId, UUID.randomUUID(), userId);
        createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID());

        List<Workspace> results = workspaceRepository.findByUserId(userId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(w -> w.getUserId().equals(userId));
    }

    @Test
    void should_findByTenantIdAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "READY");
        createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "READY");
        createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "TERMINATED");
        createWorkspace(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "READY");

        List<Workspace> readyWorkspaces =
                workspaceRepository.findByTenantIdAndStatus(tenantId, "READY");
        List<Workspace> terminatedWorkspaces =
                workspaceRepository.findByTenantIdAndStatus(tenantId, "TERMINATED");

        assertThat(readyWorkspaces).hasSize(2);
        assertThat(readyWorkspaces).allMatch(w -> w.getTenantId().equals(tenantId) && "READY".equals(w.getStatus()));
        assertThat(terminatedWorkspaces).hasSize(1);
    }

    @Test
    void should_findByTaskIdAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createWorkspace(tenantId, taskId, UUID.randomUUID(), "READY");
        createWorkspace(tenantId, taskId, UUID.randomUUID(), "TERMINATED");

        Optional<Workspace> found = workspaceRepository.findByTaskIdAndStatus(taskId, "READY");

        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo(taskId);
        assertThat(found.get().getStatus()).isEqualTo("READY");
    }

    @Test
    void should_returnEmpty_when_noWorkspaceMatchesTaskIdAndStatus() {
        Optional<Workspace> found =
                workspaceRepository.findByTaskIdAndStatus(UUID.randomUUID(), "READY");

        assertThat(found).isEmpty();
    }

    @Test
    void should_findStaleWorkspaces_when_workspacesAreOld() {
        UUID tenantId = UUID.randomUUID();

        // Create a workspace with an old createdAt timestamp
        Workspace staleWorkspace = Workspace.builder()
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .repoUrl("https://github.com/org/repo.git")
                .status("READY")
                .createdAt(Instant.now().minus(48, ChronoUnit.HOURS))
                .build();
        entityManager.persistAndFlush(staleWorkspace);

        // Create a workspace with a recent createdAt
        Workspace recentWorkspace = Workspace.builder()
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .repoUrl("https://github.com/org/repo.git")
                .status("ACTIVE")
                .build();
        entityManager.persistAndFlush(recentWorkspace);

        // Terminated workspace should NOT be returned (status filter)
        Workspace terminated = Workspace.builder()
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .providerType("DOCKER")
                .repoUrl("https://github.com/org/repo.git")
                .status("TERMINATED")
                .createdAt(Instant.now().minus(48, ChronoUnit.HOURS))
                .build();
        entityManager.persistAndFlush(terminated);

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Workspace> staleResults = workspaceRepository.findStaleWorkspaces(threshold);

        assertThat(staleResults).hasSizeGreaterThanOrEqualTo(1);
        assertThat(staleResults).allMatch(w ->
                ("READY".equals(w.getStatus()) || "ACTIVE".equals(w.getStatus()))
                        && w.getCreatedAt().isBefore(threshold));
    }

    @Test
    void should_deleteWorkspace_when_exists() {
        UUID tenantId = UUID.randomUUID();
        Workspace workspace = createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID());
        UUID workspaceId = workspace.getId();

        workspaceRepository.deleteById(workspaceId);
        entityManager.flush();

        assertThat(workspaceRepository.findById(workspaceId)).isEmpty();
    }

    @Test
    void should_updateWorkspace_when_statusChanged() {
        UUID tenantId = UUID.randomUUID();
        Workspace workspace = createWorkspace(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CREATING");

        workspace.setStatus("READY");
        workspace.setContainerId("docker-container-abc123");

        workspaceRepository.save(workspace);
        entityManager.flush();
        entityManager.clear();

        Workspace updated = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("READY");
        assertThat(updated.getContainerId()).isEqualTo("docker-container-abc123");
    }

    @Test
    void should_returnEmptyList_when_noWorkspacesForTask() {
        List<Workspace> results = workspaceRepository.findByTaskId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noWorkspacesForUser() {
        List<Workspace> results = workspaceRepository.findByUserId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }
}
