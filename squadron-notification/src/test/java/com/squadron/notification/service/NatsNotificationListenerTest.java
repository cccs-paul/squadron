package com.squadron.notification.service;

import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.ReviewUpdatedEvent;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import com.squadron.common.util.JsonUtils;
import com.squadron.notification.dto.SendNotificationRequest;
import com.squadron.notification.entity.NotificationPreference;
import com.squadron.notification.repository.NotificationPreferenceRepository;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private Connection natsConnection;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private Subscription subscription;

    @Captor
    private ArgumentCaptor<MessageHandler> handlerCaptor;

    @Captor
    private ArgumentCaptor<SendNotificationRequest> requestCaptor;

    private NatsNotificationListener listener;

    @BeforeEach
    void setUp() {
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(dispatcher.subscribe(anyString(), any(MessageHandler.class))).thenReturn(subscription);
        listener = new NatsNotificationListener(natsConnection, notificationService, preferenceRepository);
    }

    @Test
    void should_subscribeToNatsSubjects_when_initialized() {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), any(MessageHandler.class));
        verify(dispatcher).subscribe(eq("squadron.reviews.updated"), any(MessageHandler.class));
        verify(dispatcher).subscribe(eq("squadron.agents.completed"), any(MessageHandler.class));
        verify(dispatcher).subscribe(eq("squadron.git.events"), any(MessageHandler.class));
    }

    @Test
    void should_sendNotification_when_taskStateChangedEventReceived() throws Exception {
        listener.setupSubscriptions();

        // Capture the handler for task state changes
        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        verify(notificationService, atLeastOnce()).sendNotification(any());
    }

    @Test
    void should_skipNotification_when_taskStateChangedEventHasNoUser() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(UUID.randomUUID());
        event.setFromState("PLANNING");
        event.setToState("CODING");
        event.setTriggeredBy(null);
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        handler.onMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_sendNotification_when_agentCompletedEventReceived() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.agents.completed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        verify(notificationService, atLeastOnce()).sendNotification(any());
    }

    @Test
    void should_sendToMultipleChannels_when_preferencesEnabled() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.agents.completed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        // Should send IN_APP and EMAIL (2 channels enabled)
        verify(notificationService, times(2)).sendNotification(any());
    }

    @Test
    void should_handleReviewUpdatedEvent_when_received() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.reviews.updated"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        event.setReviewId(UUID.randomUUID());
        event.setTaskId(UUID.randomUUID());
        event.setReviewerType("AI");
        event.setStatus("APPROVED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        handler.onMessage(message);

        // Review events now send IN_APP notification for the tenant
        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_handleInvalidJson_when_messageReceived() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        Message message = mock(Message.class);
        when(message.getData()).thenReturn("invalid json".getBytes(StandardCharsets.UTF_8));

        // Should not throw, just log error
        handler.onMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_handleGitEvent_prMerged() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.git.events"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        SquadronEvent event = new SquadronEvent();
        event.setEventType("PR_MERGED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        handler.onMessage(message);

        verify(notificationService, times(1)).sendNotification(requestCaptor.capture());
        SendNotificationRequest request = requestCaptor.getValue();
        assert request.getSubject().equals("Pull request merged");
        assert request.getChannel().equals("IN_APP");
        assert request.getUserId() == null; // tenant-level notification
    }

    @Test
    void should_handleGitEvent_prCreated() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.git.events"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        SquadronEvent event = new SquadronEvent();
        event.setEventType("PR_CREATED");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        handler.onMessage(message);

        verify(notificationService, times(1)).sendNotification(requestCaptor.capture());
        SendNotificationRequest request = requestCaptor.getValue();
        assert request.getSubject().equals("Pull request created");
        assert request.getChannel().equals("IN_APP");
        assert request.getUserId() == null;
    }

    @Test
    void should_handleGitEvent_unknownType_noNotification() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.git.events"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

        SquadronEvent event = new SquadronEvent();
        event.setEventType("UNKNOWN_GIT_EVENT");
        event.setTenantId(UUID.randomUUID());

        Message message = mock(Message.class);
        when(message.getData()).thenReturn(JsonUtils.toJson(event).getBytes(StandardCharsets.UTF_8));

        handler.onMessage(message);

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_skipMutedEventType() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        // Should NOT send any notification because TASK_STATE_CHANGED is muted
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void should_sendNotification_whenEventNotMuted() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        // Should send IN_APP notification since TASK_STATE_CHANGED is not in muted list
        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_handleReviewUpdated_sendsInAppNotification() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.reviews.updated"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

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
    void should_sendNotification_whenMutedEventTypesIsNull() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        verify(notificationService, times(1)).sendNotification(any());
    }

    @Test
    void should_sendNotification_whenMutedEventTypesIsEmpty() throws Exception {
        listener.setupSubscriptions();

        verify(dispatcher).subscribe(eq("squadron.tasks.state-changed"), handlerCaptor.capture());
        MessageHandler handler = handlerCaptor.getValue();

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

        handler.onMessage(message);

        verify(notificationService, times(1)).sendNotification(any());
    }
}
