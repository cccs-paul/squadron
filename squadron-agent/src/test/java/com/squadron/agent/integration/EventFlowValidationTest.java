package com.squadron.agent.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.listener.TaskStateDispatcher;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.CodingAgentService;
import com.squadron.agent.service.MergeService;
import com.squadron.agent.service.PlanService;
import com.squadron.agent.service.QAAgentService;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cross-service event flow validation test.
 * <p>
 * Uses a real NATS server (via Testcontainers) to verify that the unified
 * {@link TaskStateDispatcher} correctly routes events to the appropriate
 * agent handlers based on the target workflow state.
 * Service dependencies (AgentService, CodingAgentService, etc.) are mocked so
 * we only validate the event routing and deserialization, not downstream logic.
 */
@ExtendWith(MockitoExtension.class)
@Testcontainers
class EventFlowValidationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> natsContainer = new GenericContainer<>("nats:latest")
            .withExposedPorts(4222)
            .withCommand("-js");

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Mocked service dependencies
    @Mock private AgentService agentService;
    @Mock private PlanService planService;
    @Mock private CodingAgentService codingAgentService;
    @Mock private ReviewAgentService reviewAgentService;
    @Mock private QAAgentService qaAgentService;
    @Mock private MergeService mergeService;

    private Connection publisherConnection;
    private Connection subscriberConnection;
    private JetStreamSubscriber jetStreamSubscriber;

    // Unified dispatcher under test
    private TaskStateDispatcher taskStateDispatcher;

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
        publisherConnection = Nats.connect(natsUrl);
        subscriberConnection = Nats.connect(natsUrl);

        // Create JetStream stream covering the event subjects
        JetStreamManagement jsm = subscriberConnection.jetStreamManagement();
        try {
            jsm.deleteStream("TASKS");
        } catch (Exception ignored) {
            // Stream may not exist yet
        }
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name("TASKS")
                .subjects("squadron.tasks.>")
                .storageType(StorageType.Memory)
                .build();
        jsm.addStream(streamConfig);

        // Create JetStreamSubscriber with JetStream enabled
        JetStream js = subscriberConnection.jetStream();
        jetStreamSubscriber = new JetStreamSubscriber(subscriberConnection);
        jetStreamSubscriber.setJetStream(js);

        // Instantiate the unified dispatcher with real NATS subscriber + mocked services
        taskStateDispatcher = new TaskStateDispatcher(
                jetStreamSubscriber, objectMapper,
                agentService, planService,
                codingAgentService, reviewAgentService,
                qaAgentService, mergeService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (subscriberConnection != null) {
            try {
                subscriberConnection.close();
            } catch (Exception ignored) {}
        }
        if (publisherConnection != null) {
            try {
                publisherConnection.close();
            } catch (Exception ignored) {}
        }
    }

    // ========================================================================
    // PLANNING state event routing
    // ========================================================================

    @Test
    void should_routePlanningEvent_toPlanningAgentHandler() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "PRIORITIZED", "PLANNING");

        publishEvent("squadron.tasks.state-changed", event);

        // The dispatcher calls agentService.chat() when state is PLANNING
        verify(agentService, timeout(5000)).chat(any(), eq(tenantId), eq(triggeredBy));
    }

    // ========================================================================
    // PROPOSE_CODE state event routing
    // ========================================================================

    @Test
    void should_routeProposeCodeEvent_toCodingAgentHandler() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "PLANNING", "PROPOSE_CODE");

        publishEvent("squadron.tasks.state-changed", event);

        verify(codingAgentService, timeout(5000)).executeCodeGeneration(any(TaskStateChangedEvent.class));
    }

    // ========================================================================
    // REVIEW state event routing
    // ========================================================================

    @Test
    void should_routeReviewEvent_toReviewAgentHandler() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "PROPOSE_CODE", "REVIEW");

        publishEvent("squadron.tasks.state-changed", event);

        verify(reviewAgentService, timeout(5000)).executeReview(any(TaskStateChangedEvent.class));
    }

    // ========================================================================
    // QA state event routing
    // ========================================================================

    @Test
    void should_routeQAEvent_toQAAgentHandler() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "REVIEW", "QA");

        publishEvent("squadron.tasks.state-changed", event);

        verify(qaAgentService, timeout(5000)).executeQA(any(TaskStateChangedEvent.class));
    }

    // ========================================================================
    // MERGE state event routing
    // ========================================================================

    @Test
    void should_routeMergeEvent_toMergeHandler() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "QA", "MERGE");

        publishEvent("squadron.tasks.state-changed", event);

        verify(mergeService, timeout(5000)).executeMerge(any(TaskStateChangedEvent.class));
    }

    // ========================================================================
    // Irrelevant state events should be ignored
    // ========================================================================

    @Test
    void should_ignoreIrrelevantState_DONE() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "MERGE", "DONE");

        publishEvent("squadron.tasks.state-changed", event);

        // Give time for message to be processed
        Thread.sleep(2000);

        // None of the agent services should be called for DONE state
        verifyNoInteractions(agentService);
        verifyNoInteractions(codingAgentService);
        verifyNoInteractions(reviewAgentService);
        verifyNoInteractions(qaAgentService);
        verifyNoInteractions(mergeService);
    }

    @Test
    void should_ignoreIrrelevantState_BACKLOG() throws Exception {
        taskStateDispatcher.subscribe();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent event = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "PRIORITIZED", "BACKLOG");

        publishEvent("squadron.tasks.state-changed", event);

        Thread.sleep(2000);

        verifyNoInteractions(agentService);
        verifyNoInteractions(codingAgentService);
        verifyNoInteractions(reviewAgentService);
        verifyNoInteractions(qaAgentService);
        verifyNoInteractions(mergeService);
    }

    // ========================================================================
    // Event deserialization correctness
    // ========================================================================

    @Test
    void should_correctlyDeserializeTaskStateChangedEvent() throws Exception {
        // Use a direct message handler to capture the deserialized event
        AtomicReference<TaskStateChangedEvent> capturedEvent = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        jetStreamSubscriber.subscribe("squadron.tasks.state-changed",
                "deserialize-test", "test-group", msg -> {
                    try {
                        String json = new String(msg.getData(), StandardCharsets.UTF_8);
                        TaskStateChangedEvent event = objectMapper.readValue(json, TaskStateChangedEvent.class);
                        capturedEvent.set(event);
                        latch.countDown();
                    } catch (Exception e) {
                        // ignored
                    }
                });

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        TaskStateChangedEvent sentEvent = createStateChangedEvent(
                taskId, tenantId, triggeredBy, "BACKLOG", "PRIORITIZED");
        sentEvent.setReason("Test deserialization");
        sentEvent.setSource("squadron-orchestrator");

        publishEvent("squadron.tasks.state-changed", sentEvent);

        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        TaskStateChangedEvent receivedEvent = capturedEvent.get();
        assertThat(receivedEvent).isNotNull();
        assertThat(receivedEvent.getTaskId()).isEqualTo(taskId);
        assertThat(receivedEvent.getTenantId()).isEqualTo(tenantId);
        assertThat(receivedEvent.getTriggeredBy()).isEqualTo(triggeredBy);
        assertThat(receivedEvent.getFromState()).isEqualTo("BACKLOG");
        assertThat(receivedEvent.getToState()).isEqualTo("PRIORITIZED");
        assertThat(receivedEvent.getReason()).isEqualTo("Test deserialization");
        assertThat(receivedEvent.getSource()).isEqualTo("squadron-orchestrator");
        assertThat(receivedEvent.getEventType()).isEqualTo("TASK_STATE_CHANGED");
        assertThat(receivedEvent.getEventId()).isNotNull();
        assertThat(receivedEvent.getTimestamp()).isNotNull();
    }

    @Test
    void should_handleMalformedEventData_gracefully() throws Exception {
        // Subscribe the dispatcher - it should not throw on bad data
        taskStateDispatcher.subscribe();

        // Publish invalid JSON via JetStream
        JetStream js = publisherConnection.jetStream();
        js.publish("squadron.tasks.state-changed",
                "{ this is not valid json }}}".getBytes(StandardCharsets.UTF_8));

        // Wait for processing
        Thread.sleep(2000);

        // Services should not be called
        verifyNoInteractions(agentService);
        verifyNoInteractions(codingAgentService);
    }

    @Test
    void should_handleEmptyEventData_gracefully() throws Exception {
        taskStateDispatcher.subscribe();

        // Publish empty data via JetStream
        JetStream js = publisherConnection.jetStream();
        js.publish("squadron.tasks.state-changed",
                new byte[0]);

        Thread.sleep(2000);

        verifyNoInteractions(codingAgentService);
    }

    // ========================================================================
    // Multiple events routing
    // ========================================================================

    @Test
    void should_routeMultipleEvents_toCorrectHandlers() throws Exception {
        taskStateDispatcher.subscribe();

        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        // Publish PLANNING event
        TaskStateChangedEvent planningEvent = createStateChangedEvent(
                UUID.randomUUID(), tenantId, triggeredBy, "PRIORITIZED", "PLANNING");
        publishEvent("squadron.tasks.state-changed", planningEvent);

        // Publish PROPOSE_CODE event
        TaskStateChangedEvent codingEvent = createStateChangedEvent(
                UUID.randomUUID(), tenantId, triggeredBy, "PLANNING", "PROPOSE_CODE");
        publishEvent("squadron.tasks.state-changed", codingEvent);

        // Publish REVIEW event
        TaskStateChangedEvent reviewEvent = createStateChangedEvent(
                UUID.randomUUID(), tenantId, triggeredBy, "PROPOSE_CODE", "REVIEW");
        publishEvent("squadron.tasks.state-changed", reviewEvent);

        // All three services should be invoked via the unified dispatcher
        verify(agentService, timeout(5000)).chat(any(), eq(tenantId), eq(triggeredBy));
        verify(codingAgentService, timeout(5000)).executeCodeGeneration(any(TaskStateChangedEvent.class));
        verify(reviewAgentService, timeout(5000)).executeReview(any(TaskStateChangedEvent.class));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private TaskStateChangedEvent createStateChangedEvent(UUID taskId, UUID tenantId,
                                                           UUID triggeredBy,
                                                           String fromState, String toState) {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setTriggeredBy(triggeredBy);
        event.setReason("Test transition");
        return event;
    }

    private void publishEvent(String subject, Object event) throws Exception {
        byte[] data = objectMapper.writeValueAsBytes(event);
        // Publish via JetStream so durable subscribers receive the message
        JetStream js = publisherConnection.jetStream();
        js.publish(subject, data);
        publisherConnection.flush(java.time.Duration.ofSeconds(1));
    }
}
