package com.squadron.git.repository;

import com.squadron.git.entity.BranchStrategy;
import com.squadron.git.entity.GitOperation;
import com.squadron.git.entity.PullRequestRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.git.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.git.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_git_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GitOperationRepository gitOperationRepository;

    @Autowired
    private PullRequestRecordRepository pullRequestRecordRepository;

    @Autowired
    private BranchStrategyRepository branchStrategyRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private GitOperation createGitOperation(UUID tenantId, UUID taskId, UUID workspaceId, String operationType) {
        GitOperation op = GitOperation.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .workspaceId(workspaceId)
                .operationType(operationType)
                .build();
        return entityManager.persistFlushFind(op);
    }

    private PullRequestRecord createPullRequestRecord(UUID tenantId, UUID taskId, String status) {
        PullRequestRecord pr = PullRequestRecord.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("PR-" + UUID.randomUUID().toString().substring(0, 8))
                .externalPrUrl("https://github.com/org/repo/pull/1")
                .title("Test PR for task " + taskId)
                .sourceBranch("feature/test-branch")
                .targetBranch("main")
                .status(status)
                .build();
        return entityManager.persistFlushFind(pr);
    }

    private BranchStrategy createBranchStrategy(UUID tenantId, UUID projectId, String strategyType) {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .strategyType(strategyType)
                .build();
        return entityManager.persistFlushFind(strategy);
    }

    // =========================================================================
    // GitOperationRepository Tests
    // =========================================================================

    @Test
    void should_saveGitOperation_when_validEntity() {
        GitOperation op = GitOperation.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .workspaceId(UUID.randomUUID())
                .operationType("CLONE")
                .details("{\"repoUrl\": \"https://github.com/org/repo\"}")
                .build();

        GitOperation saved = gitOperationRepository.save(op);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOperationType()).isEqualTo("CLONE");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getDetails()).contains("repoUrl");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findById_when_gitOperationExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        GitOperation op = createGitOperation(tenantId, taskId, workspaceId, "CLONE");

        Optional<GitOperation> found = gitOperationRepository.findById(op.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOperationType()).isEqualTo("CLONE");
        assertThat(found.get().getTaskId()).isEqualTo(taskId);
    }

    @Test
    void should_returnEmpty_when_gitOperationNotFound() {
        Optional<GitOperation> found = gitOperationRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTaskId_when_operationsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        createGitOperation(tenantId, taskId, workspaceId, "CLONE");
        createGitOperation(tenantId, taskId, workspaceId, "COMMIT");
        createGitOperation(tenantId, UUID.randomUUID(), workspaceId, "PUSH");

        List<GitOperation> results = gitOperationRepository.findByTaskId(taskId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(o -> o.getTaskId().equals(taskId));
    }

    @Test
    void should_findByWorkspaceId_when_operationsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        createGitOperation(tenantId, UUID.randomUUID(), workspaceId, "CLONE");
        createGitOperation(tenantId, UUID.randomUUID(), workspaceId, "PULL");
        createGitOperation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "PUSH");

        List<GitOperation> results = gitOperationRepository.findByWorkspaceId(workspaceId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(o -> o.getWorkspaceId().equals(workspaceId));
    }

    @Test
    void should_deleteGitOperation_when_exists() {
        UUID tenantId = UUID.randomUUID();
        GitOperation op = createGitOperation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CLONE");
        UUID opId = op.getId();

        gitOperationRepository.deleteById(opId);
        entityManager.flush();

        assertThat(gitOperationRepository.findById(opId)).isEmpty();
    }

    @Test
    void should_updateGitOperation_when_fieldsChanged() {
        UUID tenantId = UUID.randomUUID();
        GitOperation op = createGitOperation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CLONE");

        op.setStatus("COMPLETED");
        op.setErrorMessage(null);

        gitOperationRepository.save(op);
        entityManager.flush();
        entityManager.clear();

        GitOperation updated = gitOperationRepository.findById(op.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void should_returnEmptyList_when_noOperationsForTask() {
        List<GitOperation> results = gitOperationRepository.findByTaskId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // PullRequestRecordRepository Tests
    // =========================================================================

    @Test
    void should_savePullRequestRecord_when_validEntity() {
        PullRequestRecord pr = PullRequestRecord.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("12345")
                .externalPrUrl("https://github.com/org/repo/pull/12345")
                .title("Add new feature")
                .sourceBranch("feature/new-feature")
                .targetBranch("main")
                .build();

        PullRequestRecord saved = pullRequestRecordRepository.save(pr);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPlatform()).isEqualTo("GITHUB");
        assertThat(saved.getExternalPrId()).isEqualTo("12345");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findByTaskId_when_pullRequestExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createPullRequestRecord(tenantId, taskId, "OPEN");

        Optional<PullRequestRecord> found = pullRequestRecordRepository.findByTaskId(taskId);

        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo(taskId);
    }

    @Test
    void should_returnEmpty_when_noprForTask() {
        Optional<PullRequestRecord> found = pullRequestRecordRepository.findByTaskId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTenantIdAndStatus_when_pullRequestsExist() {
        UUID tenantId = UUID.randomUUID();
        createPullRequestRecord(tenantId, UUID.randomUUID(), "OPEN");
        createPullRequestRecord(tenantId, UUID.randomUUID(), "OPEN");
        createPullRequestRecord(tenantId, UUID.randomUUID(), "MERGED");
        createPullRequestRecord(UUID.randomUUID(), UUID.randomUUID(), "OPEN");

        List<PullRequestRecord> openPRs =
                pullRequestRecordRepository.findByTenantIdAndStatus(tenantId, "OPEN");
        List<PullRequestRecord> mergedPRs =
                pullRequestRecordRepository.findByTenantIdAndStatus(tenantId, "MERGED");

        assertThat(openPRs).hasSize(2);
        assertThat(openPRs).allMatch(pr -> pr.getTenantId().equals(tenantId) && "OPEN".equals(pr.getStatus()));
        assertThat(mergedPRs).hasSize(1);
    }

    @Test
    void should_deletePullRequestRecord_when_exists() {
        UUID tenantId = UUID.randomUUID();
        PullRequestRecord pr = createPullRequestRecord(tenantId, UUID.randomUUID(), "OPEN");
        UUID prId = pr.getId();

        pullRequestRecordRepository.deleteById(prId);
        entityManager.flush();

        assertThat(pullRequestRecordRepository.findById(prId)).isEmpty();
    }

    @Test
    void should_updatePullRequestRecord_when_statusChanged() {
        UUID tenantId = UUID.randomUUID();
        PullRequestRecord pr = createPullRequestRecord(tenantId, UUID.randomUUID(), "OPEN");

        pr.setStatus("MERGED");
        pullRequestRecordRepository.save(pr);
        entityManager.flush();
        entityManager.clear();

        PullRequestRecord updated = pullRequestRecordRepository.findById(pr.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("MERGED");
    }

    @Test
    void should_returnEmptyList_when_noOpenPRsForTenant() {
        UUID tenantId = UUID.randomUUID();
        List<PullRequestRecord> results =
                pullRequestRecordRepository.findByTenantIdAndStatus(tenantId, "OPEN");

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // BranchStrategyRepository Tests
    // =========================================================================

    @Test
    void should_saveBranchStrategy_when_validEntity() {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("GITFLOW")
                .branchPrefix("feature/")
                .targetBranch("develop")
                .developmentBranch("develop")
                .mergeMethod("SQUASH")
                .build();

        BranchStrategy saved = branchStrategyRepository.save(strategy);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStrategyType()).isEqualTo("GITFLOW");
        assertThat(saved.getBranchPrefix()).isEqualTo("feature/");
        assertThat(saved.getTargetBranch()).isEqualTo("develop");
        assertThat(saved.getDevelopmentBranch()).isEqualTo("develop");
        assertThat(saved.getMergeMethod()).isEqualTo("SQUASH");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_saveBranchStrategy_with_defaults() {
        BranchStrategy strategy = BranchStrategy.builder()
                .tenantId(UUID.randomUUID())
                .strategyType("TRUNK_BASED")
                .build();

        BranchStrategy saved = branchStrategyRepository.save(strategy);
        entityManager.flush();

        assertThat(saved.getBranchPrefix()).isEqualTo("squadron/");
        assertThat(saved.getTargetBranch()).isEqualTo("main");
        assertThat(saved.getBranchNameTemplate()).isEqualTo("{prefix}{taskId}/{slug}");
        assertThat(saved.getMergeMethod()).isEqualTo("MERGE");
    }

    @Test
    void should_findByTenantIdAndProjectId_when_strategyExists() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        createBranchStrategy(tenantId, projectId, "GITFLOW");

        Optional<BranchStrategy> found =
                branchStrategyRepository.findByTenantIdAndProjectId(tenantId, projectId);

        assertThat(found).isPresent();
        assertThat(found.get().getStrategyType()).isEqualTo("GITFLOW");
    }

    @Test
    void should_returnEmpty_when_strategyNotFoundForProject() {
        Optional<BranchStrategy> found =
                branchStrategyRepository.findByTenantIdAndProjectId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTenantIdAndProjectIdIsNull_when_defaultStrategyExists() {
        UUID tenantId = UUID.randomUUID();
        BranchStrategy defaultStrategy = BranchStrategy.builder()
                .tenantId(tenantId)
                .strategyType("TRUNK_BASED")
                .build();
        entityManager.persistAndFlush(defaultStrategy);

        Optional<BranchStrategy> found =
                branchStrategyRepository.findByTenantIdAndProjectIdIsNull(tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isNull();
        assertThat(found.get().getStrategyType()).isEqualTo("TRUNK_BASED");
    }

    @Test
    void should_findByTenantId_when_strategiesExist() {
        UUID tenantId = UUID.randomUUID();
        createBranchStrategy(tenantId, UUID.randomUUID(), "GITFLOW");
        createBranchStrategy(tenantId, UUID.randomUUID(), "TRUNK_BASED");
        createBranchStrategy(UUID.randomUUID(), UUID.randomUUID(), "GITFLOW");

        List<BranchStrategy> results = branchStrategyRepository.findByTenantId(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getTenantId().equals(tenantId));
    }

    @Test
    void should_deleteBranchStrategy_when_exists() {
        UUID tenantId = UUID.randomUUID();
        BranchStrategy strategy = createBranchStrategy(tenantId, UUID.randomUUID(), "GITFLOW");
        UUID strategyId = strategy.getId();

        branchStrategyRepository.deleteById(strategyId);
        entityManager.flush();

        assertThat(branchStrategyRepository.findById(strategyId)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noStrategiesForTenant() {
        List<BranchStrategy> results = branchStrategyRepository.findByTenantId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }
}
