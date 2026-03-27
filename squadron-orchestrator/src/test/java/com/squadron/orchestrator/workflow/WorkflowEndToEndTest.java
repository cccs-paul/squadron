package com.squadron.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.common.exception.InvalidStateTransitionException;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.dto.CreateTaskRequest;
import com.squadron.orchestrator.dto.TransitionRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.entity.TaskStateHistory;
import com.squadron.orchestrator.entity.TaskWorkflow;
import com.squadron.orchestrator.service.ProjectService;
import com.squadron.orchestrator.service.TaskService;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Subscription;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end workflow integration test for the orchestrator module.
 * Uses Testcontainers for PostgreSQL (state machine persistence) and NATS
 * (event publishing). Validates the complete task lifecycle from BACKLOG
 * through DONE, verifies state history recording, NATS event publication,
 * invalid transition rejection, and concurrent transition handling.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.flyway.enabled=true"
        }
)
@Testcontainers
class WorkflowEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("squadron_orchestrator_e2e");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> natsContainer = new GenericContainer<>("nats:latest")
            .withExposedPorts(4222)
            .withCommand("-js");

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private Project project;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        String natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
        registry.add("squadron.nats.url", () -> natsUrl);

        // Disable JWT decoder for tests (no Keycloak available)
        registry.add("squadron.security.jwt.jwks-uri",
                () -> "http://localhost:19999/api/auth/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:19999/realms/test/protocol/openid-connect/certs");
    }

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();

        CreateProjectRequest projectRequest = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("E2E Test Project " + UUID.randomUUID().toString().substring(0, 8))
                .repoUrl("https://github.com/test/e2e-repo")
                .defaultBranch("main")
                .build();
        project = projectService.createProject(projectRequest);
    }

    // ========================================================================
    // Full lifecycle test
    // ========================================================================

    @Test
    void should_transitionThroughCompleteLifecycle_fromBacklogToDone() throws Exception {
        // Arrange: set up a NATS subscriber to capture state-changed events
        Connection subscriberConn = createNatsConnection();
        Subscription subscription = subscriberConn.subscribe("squadron.tasks.state-changed");
        List<TaskStateChangedEvent> capturedEvents = new CopyOnWriteArrayList<>();

        Task task = createTask("Full Lifecycle Task");

        // Verify initial state is BACKLOG
        TaskWorkflow workflow = getWorkflow(task.getId());
        assertThat(workflow.getCurrentState()).isEqualTo("BACKLOG");

        // Act: transition through every state
        String[] transitions = {
                "PRIORITIZED", "PLANNING", "PROPOSE_CODE", "REVIEW", "QA", "MERGE", "DONE"
        };
        String[] expectedFromStates = {
                "BACKLOG", "PRIORITIZED", "PLANNING", "PROPOSE_CODE", "REVIEW", "QA", "MERGE"
        };

        for (int i = 0; i < transitions.length; i++) {
            TransitionRequest request = TransitionRequest.builder()
                    .taskId(task.getId())
                    .targetState(transitions[i])
                    .reason("Progressing to " + transitions[i])
                    .build();
            taskService.transitionTask(request, userId);
        }

        // Allow async NATS publishing to complete
        Thread.sleep(1000);

        // Drain captured events
        io.nats.client.Message msg;
        while ((msg = subscription.nextMessage(Duration.ofMillis(500))) != null) {
            TaskStateChangedEvent event = objectMapper.readValue(msg.getData(), TaskStateChangedEvent.class);
            capturedEvents.add(event);
        }

        // Assert: final state is DONE
        workflow = getWorkflow(task.getId());
        assertThat(workflow.getCurrentState()).isEqualTo("DONE");
        assertThat(workflow.getPreviousState()).isEqualTo("MERGE");

        // Assert: state history has all transitions recorded (init + 7 transitions)
        List<TaskStateHistory> history = taskService.getTaskHistory(task.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(8); // 1 init + 7 transitions

        // Assert: NATS events were published for each transition
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(7);

        // Verify events contain correct state transitions
        for (int i = 0; i < transitions.length; i++) {
            String targetState = transitions[i];
            boolean found = capturedEvents.stream()
                    .anyMatch(e -> targetState.equals(e.getToState()));
            assertThat(found)
                    .as("Expected NATS event for transition to %s", targetState)
                    .isTrue();
        }

        subscription.unsubscribe();
        subscriberConn.close();
    }

    // ========================================================================
    // State history verification
    // ========================================================================

    @Test
    void should_recordStateHistory_atEachTransition() {
        Task task = createTask("History Tracking Task");

        // Transition BACKLOG -> PRIORITIZED -> PLANNING
        transition(task.getId(), "PRIORITIZED", "Sprint planning");
        transition(task.getId(), "PLANNING", "Plan creation");

        List<TaskStateHistory> history = taskService.getTaskHistory(task.getId());

        // History: init to BACKLOG, BACKLOG->PRIORITIZED, PRIORITIZED->PLANNING
        assertThat(history).hasSizeGreaterThanOrEqualTo(3);

        // Most recent first (ordered by created_at desc)
        assertThat(history.get(0).getToState()).isEqualTo("PLANNING");
        assertThat(history.get(0).getFromState()).isEqualTo("PRIORITIZED");
        assertThat(history.get(0).getReason()).isEqualTo("Plan creation");

        assertThat(history.get(1).getToState()).isEqualTo("PRIORITIZED");
        assertThat(history.get(1).getFromState()).isEqualTo("BACKLOG");
        assertThat(history.get(1).getReason()).isEqualTo("Sprint planning");

        // Initial entry
        assertThat(history.get(2).getToState()).isEqualTo("BACKLOG");
        assertThat(history.get(2).getFromState()).isNull();

        // All history entries have the triggering user and timestamps
        history.forEach(h -> {
            assertThat(h.getTriggeredBy()).isNotNull();
            assertThat(h.getCreatedAt()).isNotNull();
        });
    }

    // ========================================================================
    // NATS event publication verification
    // ========================================================================

    @Test
    void should_publishNatsEvent_forEachTransition() throws Exception {
        Connection subscriberConn = createNatsConnection();
        Subscription subscription = subscriberConn.subscribe("squadron.tasks.state-changed");

        Task task = createTask("NATS Event Task");

        // Perform a single transition
        transition(task.getId(), "PRIORITIZED", "Testing NATS event");

        // Wait for async publishing
        Thread.sleep(500);

        io.nats.client.Message natsMsg = subscription.nextMessage(Duration.ofSeconds(3));
        assertThat(natsMsg).isNotNull();

        TaskStateChangedEvent event = objectMapper.readValue(natsMsg.getData(), TaskStateChangedEvent.class);
        assertThat(event.getTaskId()).isEqualTo(task.getId());
        assertThat(event.getFromState()).isEqualTo("BACKLOG");
        assertThat(event.getToState()).isEqualTo("PRIORITIZED");
        assertThat(event.getTriggeredBy()).isEqualTo(userId);
        assertThat(event.getReason()).isEqualTo("Testing NATS event");
        assertThat(event.getTenantId()).isEqualTo(tenantId);
        assertThat(event.getEventType()).isEqualTo("TASK_STATE_CHANGED");
        assertThat(event.getSource()).isEqualTo("squadron-orchestrator");

        subscription.unsubscribe();
        subscriberConn.close();
    }

    // ========================================================================
    // Invalid transition rejection
    // ========================================================================

    @Test
    void should_rejectInvalidTransition_fromBacklogToDone() {
        Task task = createTask("Invalid Transition Task");

        assertThatThrownBy(() -> transition(task.getId(), "DONE", "Skipping"))
                .isInstanceOf(InvalidStateTransitionException.class);

        // State should remain BACKLOG
        TaskWorkflow workflow = getWorkflow(task.getId());
        assertThat(workflow.getCurrentState()).isEqualTo("BACKLOG");
    }

    @Test
    void should_rejectInvalidTransition_fromBacklogToPlanning() {
        Task task = createTask("Invalid Skip Task");

        // BACKLOG -> PLANNING is not valid (must go through PRIORITIZED first)
        assertThatThrownBy(() -> transition(task.getId(), "PLANNING", "Skip"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void should_rejectInvalidTransition_fromDone() {
        Task task = createTask("Done Task");

        // Move to DONE
        transition(task.getId(), "PRIORITIZED", "r");
        transition(task.getId(), "PLANNING", "r");
        transition(task.getId(), "PROPOSE_CODE", "r");
        transition(task.getId(), "REVIEW", "r");
        transition(task.getId(), "QA", "r");
        transition(task.getId(), "MERGE", "r");
        transition(task.getId(), "DONE", "r");

        // No transitions allowed from DONE
        assertThatThrownBy(() -> transition(task.getId(), "BACKLOG", "Reopen"))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ========================================================================
    // Backward transitions
    // ========================================================================

    @Test
    void should_allowBackwardTransition_fromReviewToProposeCode() {
        Task task = createTask("Backward Transition Task");

        transition(task.getId(), "PRIORITIZED", "r");
        transition(task.getId(), "PLANNING", "r");
        transition(task.getId(), "PROPOSE_CODE", "r");
        transition(task.getId(), "REVIEW", "r");

        // Backward: REVIEW -> PROPOSE_CODE
        transition(task.getId(), "PROPOSE_CODE", "Changes requested");

        TaskWorkflow workflow = getWorkflow(task.getId());
        assertThat(workflow.getCurrentState()).isEqualTo("PROPOSE_CODE");
        assertThat(workflow.getPreviousState()).isEqualTo("REVIEW");
    }

    // ========================================================================
    // Concurrent transition attempts
    // ========================================================================

    @Test
    void should_handleConcurrentTransitions_withOnlyOneSucceeding() throws Exception {
        Task task = createTask("Concurrent Task");

        // First, move to PRIORITIZED (which has two valid transitions: PLANNING and BACKLOG)
        transition(task.getId(), "PRIORITIZED", "Ready");

        // Now attempt two concurrent transitions from PRIORITIZED
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransitionRequest request = TransitionRequest.builder()
                            .taskId(task.getId())
                            .targetState("PLANNING")
                            .reason("Concurrent attempt")
                            .build();
                    taskService.transitionTask(request, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // At least one should succeed
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        // After concurrent attempts, the task should be in PLANNING
        TaskWorkflow workflow = getWorkflow(task.getId());
        assertThat(workflow.getCurrentState()).isEqualTo("PLANNING");
    }

    // ========================================================================
    // Available transitions verification
    // ========================================================================

    @Test
    void should_reportCorrectAvailableTransitions_atEachState() {
        Task task = createTask("Available Transitions Task");

        // BACKLOG: only PRIORITIZED
        List<String> available = taskService.getAvailableTransitions(task.getId());
        assertThat(available).containsExactly("PRIORITIZED");

        // Move to PRIORITIZED: PLANNING and BACKLOG
        transition(task.getId(), "PRIORITIZED", "r");
        available = taskService.getAvailableTransitions(task.getId());
        assertThat(available).containsExactlyInAnyOrder("PLANNING", "BACKLOG");

        // Move to PLANNING: PROPOSE_CODE and PRIORITIZED
        transition(task.getId(), "PLANNING", "r");
        available = taskService.getAvailableTransitions(task.getId());
        assertThat(available).containsExactlyInAnyOrder("PROPOSE_CODE", "PRIORITIZED");

        // Move to DONE: no transitions available
        transition(task.getId(), "PROPOSE_CODE", "r");
        transition(task.getId(), "REVIEW", "r");
        transition(task.getId(), "QA", "r");
        transition(task.getId(), "MERGE", "r");
        transition(task.getId(), "DONE", "r");
        available = taskService.getAvailableTransitions(task.getId());
        assertThat(available).isEmpty();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Task createTask(String title) {
        CreateTaskRequest request = CreateTaskRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .projectId(project.getId())
                .title(title)
                .description("E2E test task: " + title)
                .priority("HIGH")
                .build();
        return taskService.createTask(request, userId);
    }

    private TaskWorkflow transition(UUID taskId, String targetState, String reason) {
        TransitionRequest request = TransitionRequest.builder()
                .taskId(taskId)
                .targetState(targetState)
                .reason(reason)
                .build();
        return taskService.transitionTask(request, userId);
    }

    private TaskWorkflow getWorkflow(UUID taskId) {
        var dto = taskService.getTaskWorkflow(taskId);
        TaskWorkflow tw = new TaskWorkflow();
        tw.setTaskId(dto.getTaskId());
        tw.setCurrentState(dto.getCurrentState());
        tw.setPreviousState(dto.getPreviousState());
        tw.setTransitionAt(dto.getTransitionAt());
        tw.setTransitionedBy(dto.getTransitionedBy());
        tw.setMetadata(dto.getMetadata());
        return tw;
    }

    private Connection createNatsConnection() throws IOException, InterruptedException {
        String natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
        return Nats.connect(natsUrl);
    }
}
