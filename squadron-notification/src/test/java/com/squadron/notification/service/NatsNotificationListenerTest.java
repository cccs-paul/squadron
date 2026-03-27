package com.squadron.notification.service;

import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.ReviewUpdatedEvent;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.common.util.JsonUtils;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NatsNotificationListenerTest {

    @Mock
    private JetStreamSubscriber jetStreamSubscriber;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Captor
    private ArgumentCaptor<SendNotificationRequest> requestCaptor;

    private NatsNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new NatsNotificationListener(jetStreamSubscriber, notificationService, preferenceRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_subscribeToNatsSubjects_when_initialized() {
        listener.setupSubscriptions();

        verify(jetStreamSubscriber).subscribe(
                eq(NatsNotificationListener.TASK_STATE_CHANGED_SUBJECT),
                eq("notification-task-state"), eq("squadron-notification"), any(Consumer.class));
        verify(jetStreamSubscriber).subscribe(
                eq(NatsNotificationListener.REVIEWS_UPDATED_SUBJECT),
                eq("notification-review-updated"), eq("squadron-notification"), any(Consumer.class));
        verify(jetStreamSubscriber).subscribe(
                eq(NatsNotificationListener.AGENTS_COMPLETED_SUBJECT),
                eq("notification-agent-completed"), eq("squadron-notification"), any(Consumer.class));
        verify(jetStreamSubscriber).subscribe(
                eq(NatsNotificationListener.GIT_EVENTS_SUBJECT),
                eq("notification-git-events"), eq("squadron-notification"), any(Consumer.class));
    }

    @Test
    void should_sendNotification_when_taskStateChangedEventReceived() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(userId);
        event.setTenantId(tenantId);

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        // No preferences found, defaults to IN_APP
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        listener.handleTaskStateChangedMessage(message);

        verify(notificationService, atLeastOnce()).sendNotification(any());
    }

    @Test
    void should_skipNotification_when_taskStateChangedEventHasNoUser() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(null);
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleTaskStateChangedMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_sendNotification_when_agentCompletedEventReceived() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTaskId(taskId);
        event.setUserId(userId);
        event.setAgentType("CODER");
        event.setSuccess(true);
        event.setTokenCount(1500);
        event.setTenantId(tenantId);

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        listener.handleAgentCompletedMessage(message);

        verify(notificationService, atLeastOnce()).sendNotification(any());
    }

    @Test
    void should_sendToMultipleChannels_when_preferencesEnabled() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AgentCompletedEvent event = new AgentCompletedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setUserId(userId);
        event.setAgentType("CODER");
        event.setSuccess(true);
        event.setTokenCount(1000);
        event.setTenantId(tenantId);

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(true)
                .enableSlack(false)
                .enableTeams(false)
                .build();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        listener.handleAgentCompletedMessage(message);

        // Should send IN_APP and EMAIL (2 channels enabled)
        verify(notificationService, times(2)).sendNotification(any());
    }

    @Test
    void should_handleReviewUpdatedEvent_when_received() {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        event.setReviewId(UUID.randomUUID());
        event.setTaskId(UUID.randomUUID());
        event.setReviewerType("AI");
        event.setStatus("APPROVED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleReviewUpdatedMessage(message);

        // Review events now send IN_APP notification for the tenant
        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_handleInvalidJson_when_messageReceived() {
        Message message = mock(Message.class);
        when(message.getData()).thenReturn("invalid json".getBytes(StandardCharsets.UTF_8));

        // Should not throw, just log error
        listener.handleTaskStateChangedMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_handleGitEvent_prMerged() {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("PR_MERGED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleGitEventMessage(message);

        verify(notificationService, times(1)).sendNotification(requestCaptor.capture());
        SendNotificationRequest request = requestCaptor.getValue();
        assert request.getSubject().equals("Pull request merged");
        assert request.getChannel().equals("IN_APP");
        assert request.getUserId() == null; // tenant-level notification
    }

    @Test
    void should_handleGitEvent_prCreated() {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("PR_CREATED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleGitEventMessage(message);

        verify(notificationService, times(1)).sendNotification(requestCaptor.capture());
        SendNotificationRequest request = requestCaptor.getValue();
        assert request.getSubject().equals("Pull request created");
        assert request.getChannel().equals("IN_APP");
        assert request.getUserId() == null;
    }

    @Test
    void should_handleGitEvent_unknownType_noNotification() {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("UNKNOWN_GIT_EVENT");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleGitEventMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_skipMutedEventType() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(userId);
        event.setTenantId(tenantId);

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(false)
                .enableSlack(false)
                .enableTeams(false)
                .mutedEventTypes("[\"TASK_STATE_CHANGED\",\"AGENT_COMPLETED\"]")
                .build();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        listener.handleTaskStateChangedMessage(message);

        // Should NOT send any notification because TASK_STATE_CHANGED is muted
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_sendNotification_whenEventNotMuted() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(userId);
        event.setTenantId(tenantId);

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(false)
                .enableSlack(false)
                .enableTeams(false)
                .mutedEventTypes("[\"AGENT_COMPLETED\"]")  // TASK_STATE_CHANGED is NOT muted
                .build();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        listener.handleTaskStateChangedMessage(message);

        // Should send IN_APP notification since TASK_STATE_CHANGED is not in muted list
        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_handleReviewUpdated_sendsInAppNotification() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        event.setReviewId(reviewId);
        event.setTaskId(taskId);
        event.setReviewerType("AI");
        event.setStatus("APPROVED");
        event.setTenantId(tenantId);

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        listener.handleReviewUpdatedMessage(message);

        verify(notificationService, times(1)).sendNotification(requestCaptor.capture());
        SendNotificationRequest request = requestCaptor.getValue();
        assert request.getChannel().equals("IN_APP");
        assert request.getTenantId().equals(tenantId);
        assert request.getRelatedTaskId().equals(taskId);
        assert request.getEventType().equals("REVIEW_UPDATED");
        assert request.getUserId() == null; // tenant-level, no specific user
        assert request.getSubject().contains("APPROVED");
    }

    @Test
    void should_sendNotification_whenMutedEventTypesIsNull() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(userId);
        event.setTenantId(tenantId);

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(false)
                .enableSlack(false)
                .enableTeams(false)
                .mutedEventTypes(null) // no muted types
                .build();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        listener.handleTaskStateChangedMessage(message);

        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_sendNotification_whenMutedEventTypesIsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(userId);
        event.setTenantId(tenantId);

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .tenantId(tenantId)
                .enableInApp(true)
                .enableEmail(false)
                .enableSlack(false)
                .enableTeams(false)
                .mutedEventTypes("  ") // blank muted types
                .build();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        listener.handleTaskStateChangedMessage(message);

        verify(notificationService, times(1)).sendNotification(any());
    }
}
