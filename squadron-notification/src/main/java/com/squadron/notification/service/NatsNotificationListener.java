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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NatsNotificationListener {

    private final Connection natsConnection;
    private final NotificationService notificationService;
    private final NotificationPreferenceRepository preferenceRepository;

    @PostConstruct
    public void setupSubscriptions() {
        Dispatcher dispatcher = natsConnection.createDispatcher();

        dispatcher.subscribe("squadron.tasks.state-changed", message -> {
            try {
                String json = new String(message.getData(), StandardCharsets.UTF_8);
                TaskStateChangedEvent event = JsonUtils.fromJson(json, TaskStateChangedEvent.class);
                handleTaskStateChanged(event);
            } catch (Exception e) {
                log.error("Failed to process task state changed event: {}", e.getMessage(), e);
            }
        });

        dispatcher.subscribe("squadron.reviews.updated", message -> {
            try {
                String json = new String(message.getData(), StandardCharsets.UTF_8);
                ReviewUpdatedEvent event = JsonUtils.fromJson(json, ReviewUpdatedEvent.class);
                handleReviewUpdated(event);
            } catch (Exception e) {
                log.error("Failed to process review updated event: {}", e.getMessage(), e);
            }
        });

        dispatcher.subscribe("squadron.agents.completed", message -> {
            try {
                String json = new String(message.getData(), StandardCharsets.UTF_8);
                AgentCompletedEvent event = JsonUtils.fromJson(json, AgentCompletedEvent.class);
                handleAgentCompleted(event);
            } catch (Exception e) {
                log.error("Failed to process agent completed event: {}", e.getMessage(), e);
            }
        });

        dispatcher.subscribe("squadron.git.events", message -> {
            try {
                String json = new String(message.getData(), StandardCharsets.UTF_8);
                SquadronEvent event = JsonUtils.fromJson(json, SquadronEvent.class);
                handleGitEvent(event);
            } catch (Exception e) {
                log.error("Failed to process git event: {}", e.getMessage(), e);
            }
        });

        log.info("NATS notification listeners registered for task, review, agent, and git events");
    }

    private void handleTaskStateChanged(TaskStateChangedEvent event) {
        UUID userId = event.getTriggeredBy();
        if (userId == null) {
            log.debug("No user associated with task state change event, skipping notification");
            return;
        }

        String subject = String.format("Task state changed: %s -> %s", event.getFromState(), event.getToState());
        String body = String.format("Task %s transitioned from %s to %s.",
                event.getTaskId(), event.getFromState(), event.getToState());
        if (event.getReason() != null) {
            body += " Reason: " + event.getReason();
        }

        sendToEnabledChannels(userId, event.getTenantId(), subject, body,
                event.getTaskId(), "TASK_STATE_CHANGED");
    }

    private void handleReviewUpdated(ReviewUpdatedEvent event) {
        UUID tenantId = event.getTenantId();

        String subject = String.format("Review updated: %s", event.getStatus());
        String body = String.format("Review %s for task %s has been updated to status: %s (reviewer: %s).",
                event.getReviewId(), event.getTaskId(), event.getStatus(), event.getReviewerType());

        // Send IN_APP notification for the tenant (visible to all tenant users in the notification bell)
        sendNotification(tenantId, null, "IN_APP", subject, body, event.getTaskId(), "REVIEW_UPDATED");
    }

    private void handleAgentCompleted(AgentCompletedEvent event) {
        UUID userId = event.getUserId();
        if (userId == null) {
            log.debug("No user associated with agent completed event, skipping notification");
            return;
        }

        String status = event.isSuccess() ? "successfully" : "with errors";
        String subject = String.format("Agent %s completed %s", event.getAgentType(), status);
        String body = String.format("Agent %s finished processing task %s %s. Tokens used: %d.",
                event.getAgentType(), event.getTaskId(), status, event.getTokenCount());

        sendToEnabledChannels(userId, event.getTenantId(), subject, body,
                event.getTaskId(), "AGENT_COMPLETED");
    }

    private void handleGitEvent(SquadronEvent event) {
        if ("PR_MERGED".equals(event.getEventType())) {
            String subject = "Pull request merged";
            String body = "A pull request has been merged successfully.";
            // Git events don't have userId, so send tenant-level notification
            sendNotificationForTenant(event.getTenantId(), subject, body, null, "PR_MERGED");
        } else if ("PR_CREATED".equals(event.getEventType())) {
            String subject = "Pull request created";
            String body = "A new pull request has been created.";
            sendNotificationForTenant(event.getTenantId(), subject, body, null, "PR_CREATED");
        }
    }

    private void sendToEnabledChannels(UUID userId, UUID tenantId, String subject, String body,
                                        UUID taskId, String eventType) {
        Optional<NotificationPreference> prefOpt = preferenceRepository.findByUserId(userId);
        NotificationPreference pref = prefOpt.orElse(null);

        // Check if event type is muted
        if (pref != null && isEventMuted(pref, eventType)) {
            log.debug("Event type {} is muted for user {}, skipping notification", eventType, userId);
            return;
        }

        // Default: send IN_APP if no preferences exist
        boolean sendInApp = pref == null || Boolean.TRUE.equals(pref.getEnableInApp());
        boolean sendEmail = pref != null && Boolean.TRUE.equals(pref.getEnableEmail());
        boolean sendSlack = pref != null && Boolean.TRUE.equals(pref.getEnableSlack());
        boolean sendTeams = pref != null && Boolean.TRUE.equals(pref.getEnableTeams());

        if (sendInApp) {
            sendNotification(tenantId, userId, "IN_APP", subject, body, taskId, eventType);
        }
        if (sendEmail) {
            sendNotification(tenantId, userId, "EMAIL", subject, body, taskId, eventType);
        }
        if (sendSlack) {
            sendNotification(tenantId, userId, "SLACK", subject, body, taskId, eventType);
        }
        if (sendTeams) {
            sendNotification(tenantId, userId, "TEAMS", subject, body, taskId, eventType);
        }
    }

    private boolean isEventMuted(NotificationPreference pref, String eventType) {
        String mutedTypes = pref.getMutedEventTypes();
        if (mutedTypes == null || mutedTypes.isBlank()) {
            return false;
        }
        // mutedEventTypes is a JSON array stored as text, e.g. ["TASK_STATE_CHANGED","AGENT_COMPLETED"]
        try {
            return mutedTypes.contains("\"" + eventType + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    private void sendNotificationForTenant(UUID tenantId, String subject, String body,
                                            UUID taskId, String eventType) {
        // Send IN_APP notification for the tenant (tenant-level, no specific user)
        sendNotification(tenantId, null, "IN_APP", subject, body, taskId, eventType);
    }

    private void sendNotification(UUID tenantId, UUID userId, String channel,
                                   String subject, String body, UUID taskId, String eventType) {
        try {
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .tenantId(tenantId)
                    .userId(userId) // may be null for tenant-level notifications
                    .channel(channel)
                    .subject(subject)
                    .body(body)
                    .relatedTaskId(taskId)
                    .eventType(eventType)
                    .build();
            notificationService.sendNotification(request);
        } catch (Exception e) {
            log.error("Failed to send {} notification{}: {}", channel,
                    userId != null ? " to user " + userId : "", e.getMessage(), e);
        }
    }
}
