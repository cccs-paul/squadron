package com.squadron.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.ReviewUpdatedEvent;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import com.squadron.notification.service.NatsNotificationListener;
import com.squadron.notification.service.NotificationService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Notification event flow integration test.
 * <p>
 * Verifies that the NATS notification listener infrastructure correctly processes
 * events from all 4 NATS subjects:
 * <ul>
 *   <li>{@code squadron.tasks.state-changed} -> creates task state notification</li>
 *   <li>{@code squadron.reviews.updated} -> creates review notification</li>
 *   <li>{@code squadron.agents.completed} -> creates agent notification</li>
 *   <li>{@code squadron.git.events} -> creates git notification</li>
 * </ul>
 * Also validates notification preference filtering (muted event types).
 * <p>
 * Uses a real NATS server (via Testcontainers) with JetStream durable subscriptions
 * and mocked notification service / preference repository to isolate event routing
 * from delivery logic.
 */
@ExtendWith(MockitoExtension.class)
@Testcontainers
class NotificationEventFlowTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> natsContainer = new GenericContainer<>("nats:latest")
            .withExposedPorts(4222)
            .withCommand("-js");

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private Connection publisherConnection;
    private Connection subscriberConnection;
    private JetStream publisherJetStream;
    private JetStreamSubscriber jetStreamSubscriber;
    private NatsNotificationListener listener;

    @BeforeEach
    void setUp() throws Exception {
        String natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
        publisherConnection = Nats.connect(natsUrl);
        subscriberConnection = Nats.connect(natsUrl);

        // Create JetStream streams covering the 4 event subjects
        JetStreamManagement jsm = subscriberConnection.jetStreamManagement();

        createOrReplaceStream(jsm, "TASKS", "squadron.tasks.>");
        createOrReplaceStream(jsm, "REVIEWS", "squadron.reviews.>");
        createOrReplaceStream(jsm, "AGENTS", "squadron.agents.>");
        createOrReplaceStream(jsm, "GIT_EVENTS", "squadron.git.>");

        // Enable JetStream on the subscriber
        JetStream subscriberJs = subscriberConnection.jetStream();
        jetStreamSubscriber = new JetStreamSubscriber(subscriberConnection);
        jetStreamSubscriber.setJetStream(subscriberJs);

        // Create JetStream for publisher
        publisherJetStream = publisherConnection.jetStream();

        listener = new NatsNotificationListener(
                jetStreamSubscriber, notificationService, preferenceRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (subscriberConnection != null) {
            try { subscriberConnection.close(); } catch (Exception ignored) {}
        }
        if (publisherConnection != null) {
            try { publisherConnection.close(); } catch (Exception ignored) {}
        }
    }

    private void createOrReplaceStream(JetStreamManagement jsm, String streamName, String... subjects) throws Exception {
        try {
            jsm.deleteStream(streamName);
        } catch (Exception ignored) {
            // Stream may not exist yet
        }
        StreamConfiguration config = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subjects)
                .storageType(StorageType.Memory)
                .build();
        jsm.addStream(config);
    }

    // ========================================================================
    // Task state changed events
    // ========================================================================

    @Test
    void should_createTaskNotification_whenTaskStateChangedEventReceived() throws Exception {
        listener.setupSubscriptions();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        // No notification preferences -> default IN_APP
        when(preferenceRepository.findByUserId(triggeredBy)).thenReturn(Optional.empty());

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState("BACKLOG");
        event.setToState("PRIORITIZED");
        event.setTriggeredBy(triggeredBy);
        event.setReason("Sprint planning");

        publishEvent("squadron.tasks.state-changed", event);

        // Verify notification was sent
        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getUserId()).isEqualTo(triggeredBy);
        assertThat(request.getChannel()).isEqualTo("IN_APP");
        assertThat(request.getSubject()).contains("BACKLOG", "PRIORITIZED");
        assertThat(request.getBody()).contains(taskId.toString());
        assertThat(request.getBody()).contains("Sprint planning");
        assertThat(request.getEventType()).isEqualTo("TASK_STATE_CHANGED");
        assertThat(request.getRelatedTaskId()).isEqualTo(taskId);
    }

    @Test
    void should_skipTaskNotification_whenUserIdIsNull() throws Exception {
        listener.setupSubscriptions();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setSource("squadron-orchestrator");
        event.setTaskId(UUID.randomUUID());
        event.setFromState("BACKLOG");
        event.setToState("PRIORITIZED");
        event.setTriggeredBy(null); // No user
        event.setReason("Automated");

        publishEvent("squadron.tasks.state-changed", event);

        Thread.sleep(2000);
        verify(notificationService, never()).sendNotification(any());
    }

    // ========================================================================
    // Review updated events
    // ========================================================================

    @Test
    void should_createReviewNotification_whenReviewUpdatedEventReceived() throws Exception {
        listener.setupSubscriptions();

        UUID reviewId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-review");
        event.setReviewId(reviewId);
        event.setTaskId(taskId);
        event.setReviewerType("AI");
        event.setStatus("APPROVED");

        publishEvent("squadron.reviews.updated", event);

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getChannel()).isEqualTo("IN_APP");
        assertThat(request.getSubject()).contains("APPROVED");
        assertThat(request.getBody()).contains(reviewId.toString());
        assertThat(request.getBody()).contains("AI");
        assertThat(request.getEventType()).isEqualTo("REVIEW_UPDATED");
    }

    // ========================================================================
    // Agent completed events
    // ========================================================================

    @Test
    void should_createAgentNotification_whenAgentCompletedEventReceived() throws Exception {
        listener.setupSubscriptions();

        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        // No notification preferences -> default IN_APP
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-agent");
        event.setTaskId(taskId);
        event.setUserId(userId);
        event.setAgentType("CODING");
        event.setConversationId(conversationId);
        event.setSuccess(true);
        event.setTokenCount(1500);

        publishEvent("squadron.agents.completed", event);

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getUserId()).isEqualTo(userId);
        assertThat(request.getChannel()).isEqualTo("IN_APP");
        assertThat(request.getSubject()).contains("CODING", "successfully");
        assertThat(request.getBody()).contains("1500");
        assertThat(request.getEventType()).isEqualTo("AGENT_COMPLETED");
        assertThat(request.getRelatedTaskId()).isEqualTo(taskId);
    }

    @Test
    void should_indicateFailure_whenAgentCompletedWithErrors() throws Exception {
        listener.setupSubscriptions();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-agent");
        event.setTaskId(UUID.randomUUID());
        event.setUserId(userId);
        event.setAgentType("QA");
        event.setConversationId(UUID.randomUUID());
        event.setSuccess(false);
        event.setTokenCount(800);

        publishEvent("squadron.agents.completed", event);

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getSubject()).contains("with errors");
    }

    @Test
    void should_skipAgentNotification_whenUserIdIsNull() throws Exception {
        listener.setupSubscriptions();

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setSource("squadron-agent");
        event.setTaskId(UUID.randomUUID());
        event.setUserId(null); // No user
        event.setAgentType("CODING");
        event.setConversationId(UUID.randomUUID());
        event.setSuccess(true);
        event.setTokenCount(100);

        publishEvent("squadron.agents.completed", event);

        Thread.sleep(2000);
        verify(notificationService, never()).sendNotification(any());
    }

    // ========================================================================
    // Git events
    // ========================================================================

    @Test
    void should_createGitNotification_whenPRMergedEventReceived() throws Exception {
        listener.setupSubscriptions();

        UUID tenantId = UUID.randomUUID();

        SquadronEvent event = new SquadronEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("PR_MERGED");
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-git");

        publishEvent("squadron.git.events", event);

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getUserId()).isNull(); // tenant-level notification
        assertThat(request.getChannel()).isEqualTo("IN_APP");
        assertThat(request.getSubject()).contains("merged");
        assertThat(request.getEventType()).isEqualTo("PR_MERGED");
    }

    @Test
    void should_createGitNotification_whenPRCreatedEventReceived() throws Exception {
        listener.setupSubscriptions();

        UUID tenantId = UUID.randomUUID();

        SquadronEvent event = new SquadronEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("PR_CREATED");
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-git");

        publishEvent("squadron.git.events", event);

        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000)).sendNotification(captor.capture());

        SendNotificationRequest request = captor.getValue();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getSubject()).contains("created");
        assertThat(request.getEventType()).isEqualTo("PR_CREATED");
    }

    @Test
    void should_ignoreUnrecognizedGitEvent() throws Exception {
        listener.setupSubscriptions();

        SquadronEvent event = new SquadronEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BRANCH_DELETED"); // Not handled
        event.setTenantId(UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setSource("squadron-git");

        publishEvent("squadron.git.events", event);

        Thread.sleep(2000);
        verify(notificationService, never()).sendNotification(any());
    }

    // ========================================================================
    // Notification preference filtering
    // ========================================================================

    @Test
    void should_sendToMultipleChannels_whenPreferencesEnableMultiple() throws Exception {
        listener.setupSubscriptions();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        // User has email + in-app enabled
        NotificationPreference pref = NotificationPreference.builder()
                .userId(triggeredBy)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .build();
        when(preferenceRepository.findByUserId(triggeredBy)).thenReturn(Optional.of(pref));

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState("QA");
        event.setToState("MERGE");
        event.setTriggeredBy(triggeredBy);

        publishEvent("squadron.tasks.state-changed", event);

        // Should receive 2 notifications: IN_APP + EMAIL
        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000).atLeast(2)).sendNotification(captor.capture());

        var requests = captor.getAllValues();
        var channels = requests.stream()
                .map(SendNotificationRequest::getChannel)
                .toList();
        assertThat(channels).contains("IN_APP", "EMAIL");
        assertThat(channels).doesNotContain("SLACK", "TEAMS");
    }

    @Test
    void should_muteNotification_whenEventTypeIsMuted() throws Exception {
        listener.setupSubscriptions();

        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID triggeredBy = UUID.randomUUID();

        // User has TASK_STATE_CHANGED muted
        NotificationPreference pref = NotificationPreference.builder()
                .userId(triggeredBy)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(true)
                .mutedEventTypes("[\"TASK_STATE_CHANGED\"]")
                .build();
        when(preferenceRepository.findByUserId(triggeredBy)).thenReturn(Optional.of(pref));

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-orchestrator");
        event.setTaskId(taskId);
        event.setFromState("BACKLOG");
        event.setToState("PRIORITIZED");
        event.setTriggeredBy(triggeredBy);

        publishEvent("squadron.tasks.state-changed", event);

        Thread.sleep(2000);
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_muteAgentNotification_whenAgentCompletedIsMuted() throws Exception {
        listener.setupSubscriptions();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .mutedEventTypes("[\"AGENT_COMPLETED\"]")
                .build();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setTimestamp(Instant.now());
        event.setSource("squadron-agent");
        event.setTaskId(UUID.randomUUID());
        event.setUserId(userId);
        event.setAgentType("CODING");
        event.setConversationId(UUID.randomUUID());
        event.setSuccess(true);
        event.setTokenCount(500);

        publishEvent("squadron.agents.completed", event);

        Thread.sleep(2000);
        verify(notificationService, never()).sendNotification(any());
    }

    // ========================================================================
    // Malformed events
    // ========================================================================

    @Test
    void should_handleMalformedEvent_gracefully() throws Exception {
        listener.setupSubscriptions();

        // Publish invalid JSON to each subject via JetStream
        publisherJetStream.publish("squadron.tasks.state-changed",
                "not json".getBytes(StandardCharsets.UTF_8));
        publisherJetStream.publish("squadron.reviews.updated",
                "{ broken }}}".getBytes(StandardCharsets.UTF_8));
        publisherJetStream.publish("squadron.agents.completed",
                "{}".getBytes(StandardCharsets.UTF_8));
        publisherJetStream.publish("squadron.git.events",
                "null".getBytes(StandardCharsets.UTF_8));

        Thread.sleep(2000);

        // Nothing should crash, no notifications sent
        verify(notificationService, never()).sendNotification(any());
    }

    // ========================================================================
    // All 4 subjects in sequence
    // ========================================================================

    @Test
    void should_processEventsFromAllFourSubjects() throws Exception {
        listener.setupSubscriptions();

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // 1. Task state changed
        TaskStateChangedEvent taskEvent = new TaskStateChangedEvent();
        taskEvent.setEventId(UUID.randomUUID());
        taskEvent.setTenantId(tenantId);
        taskEvent.setTimestamp(Instant.now());
        taskEvent.setSource("squadron-orchestrator");
        taskEvent.setTaskId(UUID.randomUUID());
        taskEvent.setFromState("BACKLOG");
        taskEvent.setToState("PRIORITIZED");
        taskEvent.setTriggeredBy(userId);
        publishEvent("squadron.tasks.state-changed", taskEvent);

        // 2. Review updated
        ReviewUpdatedEvent reviewEvent = new ReviewUpdatedEvent();
        reviewEvent.setEventId(UUID.randomUUID());
        reviewEvent.setTenantId(tenantId);
        reviewEvent.setTimestamp(Instant.now());
        reviewEvent.setSource("squadron-review");
        reviewEvent.setReviewId(UUID.randomUUID());
        reviewEvent.setTaskId(UUID.randomUUID());
        reviewEvent.setReviewerType("HUMAN");
        reviewEvent.setStatus("CHANGES_REQUESTED");
        publishEvent("squadron.reviews.updated", reviewEvent);

        // 3. Agent completed
        AgentCompletedEvent agentEvent = new AgentCompletedEvent();
        agentEvent.setEventId(UUID.randomUUID());
        agentEvent.setTenantId(tenantId);
        agentEvent.setTimestamp(Instant.now());
        agentEvent.setSource("squadron-agent");
        agentEvent.setTaskId(UUID.randomUUID());
        agentEvent.setUserId(userId);
        agentEvent.setAgentType("PLANNING");
        agentEvent.setConversationId(UUID.randomUUID());
        agentEvent.setSuccess(true);
        agentEvent.setTokenCount(2000);
        publishEvent("squadron.agents.completed", agentEvent);

        // 4. Git event (PR merged)
        SquadronEvent gitEvent = new SquadronEvent();
        gitEvent.setEventId(UUID.randomUUID());
        gitEvent.setEventType("PR_MERGED");
        gitEvent.setTenantId(tenantId);
        gitEvent.setTimestamp(Instant.now());
        gitEvent.setSource("squadron-git");
        publishEvent("squadron.git.events", gitEvent);

        // Verify at least one notification per event source was created
        // Task + Agent = user-level IN_APP, Review = tenant-level IN_APP, Git = tenant-level IN_APP
        ArgumentCaptor<SendNotificationRequest> captor =
                ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, timeout(5000).atLeast(4)).sendNotification(captor.capture());

        var requests = captor.getAllValues();
        var eventTypes = requests.stream()
                .map(SendNotificationRequest::getEventType)
                .toList();

        assertThat(eventTypes).contains("TASK_STATE_CHANGED");
        assertThat(eventTypes).contains("REVIEW_UPDATED");
        assertThat(eventTypes).contains("AGENT_COMPLETED");
        assertThat(eventTypes).contains("PR_MERGED");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private void publishEvent(String subject, Object event) throws Exception {
        byte[] data = objectMapper.writeValueAsBytes(event);
        publisherJetStream.publish(subject, data);
    }
}
