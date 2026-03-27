package com.squadron.agent.repository;

import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.entity.SquadronConfig;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.entity.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
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
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ContextConfiguration(classes = RepositoryIntegrationTest.TestConfig.class)
class RepositoryIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.squadron.agent.entity")
    @EnableJpaRepositories(basePackages = "com.squadron.agent.repository")
    static class TestConfig {
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_agent_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private TaskPlanRepository taskPlanRepository;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired
    private SquadronConfigRepository squadronConfigRepository;

    // ─── Helper methods ──────────────────────────────────────────────────

    private Conversation createConversation(UUID tenantId, UUID taskId, UUID userId, String agentType, String status) {
        return conversationRepository.save(Conversation.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType(agentType)
                .provider("openai")
                .model("gpt-4o")
                .status(status)
                .totalTokens(100L)
                .build());
    }

    private ConversationMessage createMessage(UUID conversationId, String role, String content, Integer tokenCount) {
        return conversationMessageRepository.save(ConversationMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .build());
    }

    // ─── ConversationRepository tests ────────────────────────────────────

    @Test
    void should_saveAndRetrieveConversation_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation saved = createConversation(tenantId, taskId, userId, "PLANNER", "ACTIVE");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Conversation> found = conversationRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getAgentType()).isEqualTo("PLANNER");
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
        assertThat(found.get().getTotalTokens()).isEqualTo(100L);
    }

    @Test
    void should_findByTaskIdAndAgentType_when_matchingConversationsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        createConversation(tenantId, taskId, userId, "PLANNER", "ACTIVE");
        createConversation(tenantId, taskId, userId, "CODER", "ACTIVE");
        createConversation(tenantId, taskId, userId, "PLANNER", "COMPLETED");

        List<Conversation> planners = conversationRepository.findByTaskIdAndAgentType(taskId, "PLANNER");
        assertThat(planners).hasSize(2);
        assertThat(planners).allMatch(c -> c.getAgentType().equals("PLANNER"));

        List<Conversation> coders = conversationRepository.findByTaskIdAndAgentType(taskId, "CODER");
        assertThat(coders).hasSize(1);
    }

    @Test
    void should_findByTaskId_when_multipleConversationsForTask() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID otherTaskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        createConversation(tenantId, taskId, userId, "PLANNER", "ACTIVE");
        createConversation(tenantId, taskId, userId, "CODER", "ACTIVE");
        createConversation(tenantId, otherTaskId, userId, "PLANNER", "ACTIVE");

        List<Conversation> results = conversationRepository.findByTaskId(taskId);
        assertThat(results).hasSize(2);
    }

    @Test
    void should_findByUserIdAndStatus_when_filteringActiveConversations() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        createConversation(tenantId, UUID.randomUUID(), userId, "PLANNER", "ACTIVE");
        createConversation(tenantId, UUID.randomUUID(), userId, "CODER", "ACTIVE");
        createConversation(tenantId, UUID.randomUUID(), userId, "REVIEWER", "COMPLETED");
        createConversation(tenantId, UUID.randomUUID(), otherUserId, "PLANNER", "ACTIVE");

        List<Conversation> activeForUser = conversationRepository.findByUserIdAndStatus(userId, "ACTIVE");
        assertThat(activeForUser).hasSize(2);
        assertThat(activeForUser).allMatch(c -> c.getUserId().equals(userId) && c.getStatus().equals("ACTIVE"));
    }

    @Test
    void should_findByIdAndTenantId_when_tenantScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation saved = createConversation(tenantId, taskId, userId, "PLANNER", "ACTIVE");

        Optional<Conversation> found = conversationRepository.findByIdAndTenantId(saved.getId(), tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());

        Optional<Conversation> notFound = conversationRepository.findByIdAndTenantId(saved.getId(), otherTenantId);
        assertThat(notFound).isEmpty();
    }

    // ─── ConversationMessageRepository tests ─────────────────────────────

    @Test
    void should_saveAndRetrieveMessage_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        Conversation conversation = createConversation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "PLANNER", "ACTIVE");

        ConversationMessage saved = createMessage(conversation.getId(), "user", "Hello, agent!", 10);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<ConversationMessage> found = conversationMessageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo("user");
        assertThat(found.get().getContent()).isEqualTo("Hello, agent!");
        assertThat(found.get().getTokenCount()).isEqualTo(10);
    }

    @Test
    void should_findByConversationIdOrderByCreatedAtAsc_when_multipleMessagesExist() {
        UUID tenantId = UUID.randomUUID();
        Conversation conversation = createConversation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CODER", "ACTIVE");

        ConversationMessage msg1 = createMessage(conversation.getId(), "user", "First message", 5);
        ConversationMessage msg2 = createMessage(conversation.getId(), "assistant", "Second message", 15);
        ConversationMessage msg3 = createMessage(conversation.getId(), "user", "Third message", 8);

        List<ConversationMessage> messages = conversationMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).isEqualTo("First message");
        assertThat(messages.get(1).getContent()).isEqualTo("Second message");
        assertThat(messages.get(2).getContent()).isEqualTo("Third message");
    }

    @Test
    void should_countByConversationId_when_messagesExist() {
        UUID tenantId = UUID.randomUUID();
        Conversation conversation = createConversation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "PLANNER", "ACTIVE");
        Conversation otherConversation = createConversation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CODER", "ACTIVE");

        createMessage(conversation.getId(), "user", "msg1", 5);
        createMessage(conversation.getId(), "assistant", "msg2", 10);
        createMessage(conversation.getId(), "user", "msg3", 5);
        createMessage(otherConversation.getId(), "user", "other msg", 5);

        long count = conversationMessageRepository.countByConversationId(conversation.getId());
        assertThat(count).isEqualTo(3);

        long otherCount = conversationMessageRepository.countByConversationId(otherConversation.getId());
        assertThat(otherCount).isEqualTo(1);
    }

    @Test
    void should_returnEmptyList_when_noMessagesForConversation() {
        UUID nonExistentConversationId = UUID.randomUUID();

        List<ConversationMessage> messages = conversationMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(nonExistentConversationId);
        assertThat(messages).isEmpty();

        long count = conversationMessageRepository.countByConversationId(nonExistentConversationId);
        assertThat(count).isZero();
    }

    @Test
    void should_saveMessageWithToolCalls_when_jsonbProvided() {
        UUID tenantId = UUID.randomUUID();
        Conversation conversation = createConversation(tenantId, UUID.randomUUID(), UUID.randomUUID(), "CODER", "ACTIVE");

        String toolCallsJson = """
                [{"name":"file_read","arguments":{"path":"/src/Main.java"}}]""";
        ConversationMessage saved = conversationMessageRepository.save(ConversationMessage.builder()
                .conversationId(conversation.getId())
                .role("assistant")
                .content(null)
                .toolCalls(toolCallsJson)
                .tokenCount(20)
                .build());

        Optional<ConversationMessage> found = conversationMessageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getToolCalls()).isEqualTo(toolCallsJson);
        assertThat(found.get().getContent()).isNull();
    }

    // ─── TaskPlanRepository tests ────────────────────────────────────────

    @Test
    void should_saveAndRetrieveTaskPlan_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskPlan saved = taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(1)
                .planContent("## Step 1\nImplement the feature\n## Step 2\nWrite tests")
                .status("DRAFT")
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<TaskPlan> found = taskPlanRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
        assertThat(found.get().getVersion()).isEqualTo(1);
        assertThat(found.get().getStatus()).isEqualTo("DRAFT");
        assertThat(found.get().getPlanContent()).contains("Step 1");
    }

    @Test
    void should_findByTaskIdOrderByVersionDesc_when_multipleVersionsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(1)
                .planContent("Plan v1")
                .status("SUPERSEDED")
                .build());
        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(2)
                .planContent("Plan v2")
                .status("APPROVED")
                .build());
        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(3)
                .planContent("Plan v3")
                .status("DRAFT")
                .build());

        List<TaskPlan> plans = taskPlanRepository.findByTaskIdOrderByVersionDesc(taskId);
        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).getVersion()).isEqualTo(3);
        assertThat(plans.get(1).getVersion()).isEqualTo(2);
        assertThat(plans.get(2).getVersion()).isEqualTo(1);
    }

    @Test
    void should_findByTaskIdAndStatus_when_matchingPlanExists() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(1)
                .planContent("Old plan")
                .status("SUPERSEDED")
                .build());
        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(2)
                .planContent("Approved plan")
                .status("APPROVED")
                .build());

        Optional<TaskPlan> approved = taskPlanRepository.findByTaskIdAndStatus(taskId, "APPROVED");
        assertThat(approved).isPresent();
        assertThat(approved.get().getPlanContent()).isEqualTo("Approved plan");

        Optional<TaskPlan> nonExistent = taskPlanRepository.findByTaskIdAndStatus(taskId, "IN_PROGRESS");
        assertThat(nonExistent).isEmpty();
    }

    @Test
    void should_findFirstByTaskIdOrderByVersionDesc_when_latestVersionNeeded() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(1)
                .planContent("Plan v1")
                .status("SUPERSEDED")
                .build());
        taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .version(2)
                .planContent("Plan v2")
                .status("DRAFT")
                .build());

        Optional<TaskPlan> latest = taskPlanRepository.findFirstByTaskIdOrderByVersionDesc(taskId);
        assertThat(latest).isPresent();
        assertThat(latest.get().getVersion()).isEqualTo(2);
        assertThat(latest.get().getPlanContent()).isEqualTo("Plan v2");
    }

    @Test
    void should_saveTaskPlanWithApproval_when_approvedBySet() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        Instant approvedAt = Instant.now();

        Conversation conversation = createConversation(tenantId, taskId, UUID.randomUUID(), "PLANNER", "COMPLETED");

        TaskPlan saved = taskPlanRepository.save(TaskPlan.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .conversationId(conversation.getId())
                .version(1)
                .planContent("Approved plan content")
                .status("APPROVED")
                .approvedBy(approverId)
                .approvedAt(approvedAt)
                .build());

        Optional<TaskPlan> found = taskPlanRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getApprovedBy()).isEqualTo(approverId);
        assertThat(found.get().getApprovedAt()).isNotNull();
        assertThat(found.get().getConversationId()).isEqualTo(conversation.getId());
    }

    // ─── TokenUsageRepository tests ──────────────────────────────────────

    @Test
    void should_saveAndRetrieveTokenUsage_when_validEntity() {
        UUID tenantId = UUID.randomUUID();

        TokenUsage saved = tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .agentType("CODER")
                .modelName("gpt-4o")
                .inputTokens(500)
                .outputTokens(300)
                .totalTokens(800)
                .estimatedCost(0.024)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<TokenUsage> found = tokenUsageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAgentType()).isEqualTo("CODER");
        assertThat(found.get().getInputTokens()).isEqualTo(500);
        assertThat(found.get().getOutputTokens()).isEqualTo(300);
        assertThat(found.get().getTotalTokens()).isEqualTo(800);
        assertThat(found.get().getEstimatedCost()).isEqualTo(0.024);
    }

    @Test
    void should_findByTenantId_when_multipleRecordsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("PLANNER").inputTokens(100).outputTokens(50).totalTokens(150).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("CODER").inputTokens(200).outputTokens(100).totalTokens(300).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(otherTenantId).agentType("PLANNER").inputTokens(50).outputTokens(25).totalTokens(75).build());

        List<TokenUsage> results = tokenUsageRepository.findByTenantId(tenantId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getTenantId().equals(tenantId));
    }

    @Test
    void should_findByTenantIdAndUserId_when_userScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(userId).agentType("CODER")
                .inputTokens(100).outputTokens(50).totalTokens(150).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(userId).agentType("REVIEWER")
                .inputTokens(200).outputTokens(100).totalTokens(300).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(otherUserId).agentType("CODER")
                .inputTokens(50).outputTokens(25).totalTokens(75).build());

        List<TokenUsage> results = tokenUsageRepository.findByTenantIdAndUserId(tenantId, userId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getUserId().equals(userId));
    }

    @Test
    void should_findByTenantIdAndTeamId_when_teamScopedQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();

        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).teamId(teamId).agentType("CODER")
                .inputTokens(100).outputTokens(50).totalTokens(150).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).teamId(teamId).agentType("PLANNER")
                .inputTokens(200).outputTokens(100).totalTokens(300).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).teamId(otherTeamId).agentType("CODER")
                .inputTokens(50).outputTokens(25).totalTokens(75).build());

        List<TokenUsage> results = tokenUsageRepository.findByTenantIdAndTeamId(tenantId, teamId);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getTeamId().equals(teamId));
    }

    @Test
    void should_findByTenantIdAndAgentType_when_agentTypeScopedQuery() {
        UUID tenantId = UUID.randomUUID();

        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("CODER").inputTokens(100).outputTokens(50).totalTokens(150).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("CODER").inputTokens(200).outputTokens(100).totalTokens(300).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("REVIEWER").inputTokens(50).outputTokens(25).totalTokens(75).build());

        List<TokenUsage> coders = tokenUsageRepository.findByTenantIdAndAgentType(tenantId, "CODER");
        assertThat(coders).hasSize(2);

        List<TokenUsage> reviewers = tokenUsageRepository.findByTenantIdAndAgentType(tenantId, "REVIEWER");
        assertThat(reviewers).hasSize(1);
    }

    @Test
    void should_findByTenantIdAndCreatedAtBetween_when_dateRangeProvided() {
        UUID tenantId = UUID.randomUUID();

        // Save records and then retrieve to get their actual created_at timestamps
        TokenUsage usage1 = tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("CODER").inputTokens(100).outputTokens(50).totalTokens(150).build());
        TokenUsage usage2 = tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).agentType("PLANNER").inputTokens(200).outputTokens(100).totalTokens(300).build());

        // Query with a time range that encompasses now
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);

        List<TokenUsage> results = tokenUsageRepository.findByTenantIdAndCreatedAtBetween(tenantId, start, end);
        assertThat(results).hasSize(2);

        // Query with a time range in the past (should return nothing)
        Instant pastStart = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant pastEnd = Instant.now().minus(2, ChronoUnit.HOURS);

        List<TokenUsage> pastResults = tokenUsageRepository.findByTenantIdAndCreatedAtBetween(tenantId, pastStart, pastEnd);
        assertThat(pastResults).isEmpty();
    }

    @Test
    void should_findByTenantIdAndUserIdAndCreatedAtBetween_when_userAndDateRangeProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(userId).agentType("CODER")
                .inputTokens(100).outputTokens(50).totalTokens(150).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(userId).agentType("PLANNER")
                .inputTokens(200).outputTokens(100).totalTokens(300).build());
        tokenUsageRepository.save(TokenUsage.builder()
                .tenantId(tenantId).userId(otherUserId).agentType("CODER")
                .inputTokens(50).outputTokens(25).totalTokens(75).build());

        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);

        List<TokenUsage> results = tokenUsageRepository.findByTenantIdAndUserIdAndCreatedAtBetween(
                tenantId, userId, start, end);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getUserId().equals(userId));
    }

    // ─── SquadronConfigRepository tests ──────────────────────────────────

    @Test
    void should_saveAndRetrieveSquadronConfig_when_validEntity() {
        UUID tenantId = UUID.randomUUID();
        String configJson = """
                {"agents":{"planner":{"model":"gpt-4o"},"coder":{"model":"claude-3.5-sonnet"}}}""";

        SquadronConfig saved = squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .name("default-squadron")
                .config(configJson)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<SquadronConfig> found = squadronConfigRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("default-squadron");
        assertThat(found.get().getConfig()).isEqualTo(configJson);
    }

    @Test
    void should_findByTenantIdAndTeamIdAndUserId_when_userLevelConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("user-config")
                .config("{\"theme\":\"dark\"}")
                .build());

        Optional<SquadronConfig> found = squadronConfigRepository
                .findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("user-config");

        Optional<SquadronConfig> notFound = squadronConfigRepository
                .findByTenantIdAndTeamIdAndUserId(tenantId, teamId, UUID.randomUUID());
        assertThat(notFound).isEmpty();
    }

    @Test
    void should_findByTenantIdAndTeamIdAndUserIdIsNull_when_teamLevelConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(null)
                .name("team-config")
                .config("{\"defaultModel\":\"gpt-4o\"}")
                .build());

        // Also save a user-level config for the same team to ensure it's not returned
        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(UUID.randomUUID())
                .name("user-config")
                .config("{\"theme\":\"light\"}")
                .build());

        Optional<SquadronConfig> found = squadronConfigRepository
                .findByTenantIdAndTeamIdAndUserIdIsNull(tenantId, teamId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("team-config");
        assertThat(found.get().getUserId()).isNull();
    }

    @Test
    void should_findByTenantIdAndTeamIdIsNullAndUserIdIsNull_when_tenantLevelConfigExists() {
        UUID tenantId = UUID.randomUUID();

        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .teamId(null)
                .userId(null)
                .name("tenant-default")
                .config("{\"maxTokens\":4096}")
                .build());

        // Save a team-level config to ensure it's not returned
        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId)
                .teamId(UUID.randomUUID())
                .userId(null)
                .name("team-config")
                .config("{\"maxTokens\":8192}")
                .build());

        Optional<SquadronConfig> found = squadronConfigRepository
                .findByTenantIdAndTeamIdIsNullAndUserIdIsNull(tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("tenant-default");
        assertThat(found.get().getTeamId()).isNull();
        assertThat(found.get().getUserId()).isNull();
    }

    @Test
    void should_findByTenantId_when_multipleConfigLevelsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId).name("tenant-default")
                .config("{\"level\":\"tenant\"}").build());
        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId).teamId(UUID.randomUUID()).name("team-config")
                .config("{\"level\":\"team\"}").build());
        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(tenantId).teamId(UUID.randomUUID()).userId(UUID.randomUUID())
                .name("user-config").config("{\"level\":\"user\"}").build());
        squadronConfigRepository.save(SquadronConfig.builder()
                .tenantId(otherTenantId).name("other-tenant")
                .config("{\"level\":\"tenant\"}").build());

        List<SquadronConfig> results = squadronConfigRepository.findByTenantId(tenantId);
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(c -> c.getTenantId().equals(tenantId));
    }
}
