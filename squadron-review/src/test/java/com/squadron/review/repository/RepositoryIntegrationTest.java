package com.squadron.review.repository;

import com.squadron.review.entity.QAReport;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewComment;
import com.squadron.review.entity.ReviewPolicy;
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

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@Testcontainers
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.review.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.review.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_review_test");

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
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewCommentRepository reviewCommentRepository;

    @Autowired
    private QAReportRepository qaReportRepository;

    @Autowired
    private ReviewPolicyRepository reviewPolicyRepository;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Review createReview(UUID tenantId, UUID taskId, String reviewerType, String status) {
        Review review = Review.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType(reviewerType)
                .status(status)
                .summary("Test review summary")
                .build();
        return entityManager.persistFlushFind(review);
    }

    private Review createReview(UUID tenantId, UUID taskId, String reviewerType) {
        return createReview(tenantId, taskId, reviewerType, "PENDING");
    }

    private ReviewComment createReviewComment(UUID reviewId, String severity, String body) {
        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId)
                .filePath("src/main/java/Test.java")
                .lineNumber(42)
                .body(body)
                .severity(severity)
                .category("CODE_QUALITY")
                .build();
        return entityManager.persistFlushFind(comment);
    }

    private QAReport createQAReport(UUID tenantId, UUID taskId, String verdict) {
        QAReport report = QAReport.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .verdict(verdict)
                .summary("QA report summary")
                .lineCoverage(85.0)
                .branchCoverage(70.0)
                .testsPassed(100)
                .testsFailed(2)
                .testsSkipped(1)
                .build();
        return entityManager.persistFlushFind(report);
    }

    private ReviewPolicy createReviewPolicy(UUID tenantId, UUID teamId) {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(2)
                .requireAiReview(true)
                .selfReviewAllowed(false)
                .build();
        return entityManager.persistFlushFind(policy);
    }

    // =========================================================================
    // ReviewRepository Tests
    // =========================================================================

    @Test
    void should_saveReview_when_validEntity() {
        Review review = Review.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .reviewerType("HUMAN")
                .summary("Looks good to me")
                .build();

        Review saved = reviewRepository.save(review);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReviewerType()).isEqualTo("HUMAN");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getSummary()).isEqualTo("Looks good to me");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_findById_when_reviewExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Review review = createReview(tenantId, taskId, "AI");

        Optional<Review> found = reviewRepository.findById(review.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getReviewerType()).isEqualTo("AI");
    }

    @Test
    void should_findByTaskId_when_reviewsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createReview(tenantId, taskId, "AI");
        createReview(tenantId, taskId, "HUMAN");
        createReview(tenantId, UUID.randomUUID(), "AI");

        List<Review> results = reviewRepository.findByTaskId(taskId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getTaskId().equals(taskId));
    }

    @Test
    void should_findByTaskIdAndReviewerType_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createReview(tenantId, taskId, "AI");
        createReview(tenantId, taskId, "AI");
        createReview(tenantId, taskId, "HUMAN");

        List<Review> aiReviews = reviewRepository.findByTaskIdAndReviewerType(taskId, "AI");
        List<Review> humanReviews = reviewRepository.findByTaskIdAndReviewerType(taskId, "HUMAN");

        assertThat(aiReviews).hasSize(2);
        assertThat(humanReviews).hasSize(1);
    }

    @Test
    void should_findByReviewerId_when_reviewsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Review r1 = Review.builder()
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .build();
        entityManager.persistAndFlush(r1);

        Review r2 = Review.builder()
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .build();
        entityManager.persistAndFlush(r2);

        List<Review> results = reviewRepository.findByReviewerId(reviewerId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getReviewerId().equals(reviewerId));
    }

    @Test
    void should_countByTaskIdAndReviewerTypeAndStatus_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createReview(tenantId, taskId, "HUMAN", "APPROVED");
        createReview(tenantId, taskId, "HUMAN", "APPROVED");
        createReview(tenantId, taskId, "HUMAN", "CHANGES_REQUESTED");
        createReview(tenantId, taskId, "AI", "APPROVED");

        long approvedHumanCount = reviewRepository.countByTaskIdAndReviewerTypeAndStatus(
                taskId, "HUMAN", "APPROVED");
        long approvedAiCount = reviewRepository.countByTaskIdAndReviewerTypeAndStatus(
                taskId, "AI", "APPROVED");

        assertThat(approvedHumanCount).isEqualTo(2);
        assertThat(approvedAiCount).isEqualTo(1);
    }

    @Test
    void should_deleteReview_when_exists() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");
        UUID reviewId = review.getId();

        reviewRepository.deleteById(reviewId);
        entityManager.flush();

        assertThat(reviewRepository.findById(reviewId)).isEmpty();
    }

    // =========================================================================
    // ReviewCommentRepository Tests
    // =========================================================================

    @Test
    void should_saveReviewComment_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");

        ReviewComment comment = ReviewComment.builder()
                .reviewId(review.getId())
                .filePath("src/main/java/Service.java")
                .lineNumber(15)
                .body("Consider using dependency injection here")
                .severity("WARNING")
                .category("DESIGN")
                .build();

        ReviewComment saved = reviewCommentRepository.save(comment);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFilePath()).isEqualTo("src/main/java/Service.java");
        assertThat(saved.getLineNumber()).isEqualTo(15);
        assertThat(saved.getBody()).isEqualTo("Consider using dependency injection here");
        assertThat(saved.getSeverity()).isEqualTo("WARNING");
        assertThat(saved.getCategory()).isEqualTo("DESIGN");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByReviewId_when_commentsExist() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");
        createReviewComment(review.getId(), "ERROR", "Bug found");
        createReviewComment(review.getId(), "WARNING", "Potential issue");

        Review otherReview = createReview(tenantId, UUID.randomUUID(), "HUMAN");
        createReviewComment(otherReview.getId(), "INFO", "Minor suggestion");

        List<ReviewComment> results = reviewCommentRepository.findByReviewId(review.getId());

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getReviewId().equals(review.getId()));
    }

    @Test
    void should_findByReviewIdAndSeverity_when_matching() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");
        createReviewComment(review.getId(), "ERROR", "Critical bug");
        createReviewComment(review.getId(), "ERROR", "Another critical bug");
        createReviewComment(review.getId(), "WARNING", "Non-critical issue");

        List<ReviewComment> errors =
                reviewCommentRepository.findByReviewIdAndSeverity(review.getId(), "ERROR");
        List<ReviewComment> warnings =
                reviewCommentRepository.findByReviewIdAndSeverity(review.getId(), "WARNING");

        assertThat(errors).hasSize(2);
        assertThat(errors).allMatch(c -> "ERROR".equals(c.getSeverity()));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void should_deleteReviewComment_when_exists() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");
        ReviewComment comment = createReviewComment(review.getId(), "ERROR", "Bug");
        UUID commentId = comment.getId();

        reviewCommentRepository.deleteById(commentId);
        entityManager.flush();

        assertThat(reviewCommentRepository.findById(commentId)).isEmpty();
    }

    @Test
    void should_cascadeDeleteComments_when_reviewDeleted() {
        UUID tenantId = UUID.randomUUID();
        Review review = createReview(tenantId, UUID.randomUUID(), "AI");
        ReviewComment comment = createReviewComment(review.getId(), "ERROR", "Bug");
        UUID commentId = comment.getId();

        // Use native SQL DELETE to trigger the database ON DELETE CASCADE constraint
        entityManager.getEntityManager().createNativeQuery("DELETE FROM reviews WHERE id = :id")
                .setParameter("id", review.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(reviewCommentRepository.findById(commentId)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noCommentsForReview() {
        List<ReviewComment> results = reviewCommentRepository.findByReviewId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // QAReportRepository Tests
    // =========================================================================

    @Test
    void should_saveQAReport_when_validEntity() {
        QAReport report = QAReport.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .verdict("PASS")
                .summary("All tests passed")
                .lineCoverage(95.5)
                .branchCoverage(88.0)
                .testsPassed(200)
                .testsFailed(0)
                .testsSkipped(3)
                .findings("{\"issues\": []}")
                .testGaps("{\"gaps\": []}")
                .coverageDetails("{\"packages\": {}}")
                .build();

        QAReport saved = qaReportRepository.save(report);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVerdict()).isEqualTo("PASS");
        assertThat(saved.getLineCoverage()).isEqualTo(95.5);
        assertThat(saved.getBranchCoverage()).isEqualTo(88.0);
        assertThat(saved.getTestsPassed()).isEqualTo(200);
        assertThat(saved.getTestsFailed()).isEqualTo(0);
        assertThat(saved.getTestsSkipped()).isEqualTo(3);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_findByTaskId_when_qaReportsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createQAReport(tenantId, taskId, "PASS");
        createQAReport(tenantId, taskId, "FAIL");
        createQAReport(tenantId, UUID.randomUUID(), "PASS");

        List<QAReport> results = qaReportRepository.findByTaskId(taskId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getTaskId().equals(taskId));
    }

    @Test
    void should_findFirstByTaskIdOrderByCreatedAtDesc_when_reportsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createQAReport(tenantId, taskId, "FAIL");
        // Small delay to ensure ordering - persist second report after first
        QAReport latest = createQAReport(tenantId, taskId, "PASS");

        Optional<QAReport> found = qaReportRepository.findFirstByTaskIdOrderByCreatedAtDesc(taskId);

        assertThat(found).isPresent();
        // The latest report should be returned
        assertThat(found.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    void should_findByTenantId_when_reportsExist() {
        UUID tenantId = UUID.randomUUID();
        createQAReport(tenantId, UUID.randomUUID(), "PASS");
        createQAReport(tenantId, UUID.randomUUID(), "FAIL");
        createQAReport(UUID.randomUUID(), UUID.randomUUID(), "PASS");

        List<QAReport> results = qaReportRepository.findByTenantId(tenantId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getTenantId().equals(tenantId));
    }

    @Test
    void should_countByTaskIdAndVerdict_when_matching() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        createQAReport(tenantId, taskId, "PASS");
        createQAReport(tenantId, taskId, "PASS");
        createQAReport(tenantId, taskId, "FAIL");

        long passCount = qaReportRepository.countByTaskIdAndVerdict(taskId, "PASS");
        long failCount = qaReportRepository.countByTaskIdAndVerdict(taskId, "FAIL");

        assertThat(passCount).isEqualTo(2);
        assertThat(failCount).isEqualTo(1);
    }

    @Test
    void should_deleteQAReport_when_exists() {
        UUID tenantId = UUID.randomUUID();
        QAReport report = createQAReport(tenantId, UUID.randomUUID(), "PASS");
        UUID reportId = report.getId();

        qaReportRepository.deleteById(reportId);
        entityManager.flush();

        assertThat(qaReportRepository.findById(reportId)).isEmpty();
    }

    @Test
    void should_returnEmptyList_when_noReportsForTenant() {
        List<QAReport> results = qaReportRepository.findByTenantId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    // =========================================================================
    // ReviewPolicyRepository Tests
    // =========================================================================

    @Test
    void should_saveReviewPolicy_when_validEntity() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .minHumanApprovals(3)
                .requireAiReview(true)
                .selfReviewAllowed(false)
                .autoRequestReviewers("[\"user1\", \"user2\"]")
                .reviewChecklist("{\"items\": [\"tests\", \"docs\"]}")
                .build();

        ReviewPolicy saved = reviewPolicyRepository.save(policy);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMinHumanApprovals()).isEqualTo(3);
        assertThat(saved.getRequireAiReview()).isTrue();
        assertThat(saved.getSelfReviewAllowed()).isFalse();
        assertThat(saved.getAutoRequestReviewers()).contains("user1");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_saveReviewPolicy_with_defaults() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(UUID.randomUUID())
                .build();

        ReviewPolicy saved = reviewPolicyRepository.save(policy);
        entityManager.flush();

        assertThat(saved.getMinHumanApprovals()).isEqualTo(1);
        assertThat(saved.getRequireAiReview()).isTrue();
        assertThat(saved.getSelfReviewAllowed()).isTrue();
    }

    @Test
    void should_findByTenantIdAndTeamId_when_policyExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        createReviewPolicy(tenantId, teamId);

        Optional<ReviewPolicy> found =
                reviewPolicyRepository.findByTenantIdAndTeamId(tenantId, teamId);

        assertThat(found).isPresent();
        assertThat(found.get().getMinHumanApprovals()).isEqualTo(2);
    }

    @Test
    void should_returnEmpty_when_policyNotFoundForTeam() {
        Optional<ReviewPolicy> found =
                reviewPolicyRepository.findByTenantIdAndTeamId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void should_findByTenantIdAndTeamIdIsNull_when_defaultPolicyExists() {
        UUID tenantId = UUID.randomUUID();
        ReviewPolicy defaultPolicy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .build();
        entityManager.persistAndFlush(defaultPolicy);

        Optional<ReviewPolicy> found =
                reviewPolicyRepository.findByTenantIdAndTeamIdIsNull(tenantId);

        assertThat(found).isPresent();
        assertThat(found.get().getTeamId()).isNull();
    }

    @Test
    void should_deleteReviewPolicy_when_exists() {
        UUID tenantId = UUID.randomUUID();
        ReviewPolicy policy = createReviewPolicy(tenantId, UUID.randomUUID());
        UUID policyId = policy.getId();

        reviewPolicyRepository.deleteById(policyId);
        entityManager.flush();

        assertThat(reviewPolicyRepository.findById(policyId)).isEmpty();
    }

    @Test
    void should_updateReviewPolicy_when_fieldsChanged() {
        UUID tenantId = UUID.randomUUID();
        ReviewPolicy policy = createReviewPolicy(tenantId, UUID.randomUUID());

        policy.setMinHumanApprovals(5);
        policy.setRequireAiReview(false);

        reviewPolicyRepository.save(policy);
        entityManager.flush();
        entityManager.clear();

        ReviewPolicy updated = reviewPolicyRepository.findById(policy.getId()).orElseThrow();
        assertThat(updated.getMinHumanApprovals()).isEqualTo(5);
        assertThat(updated.getRequireAiReview()).isFalse();
    }
}
