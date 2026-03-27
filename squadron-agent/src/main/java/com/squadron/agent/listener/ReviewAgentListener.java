package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listens for task state-change events on NATS and triggers the review agent
 * when a task transitions to the {@code REVIEW} state.
 * Uses JetStream durable subscriptions for at-least-once delivery.
 */
@Component
public class ReviewAgentListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgentListener.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.tasks.state-changed";
    static final String DURABLE_NAME = "review-agent-listener";
    static final String QUEUE_GROUP = "squadron-agent";

    private final JetStreamSubscriber jetStreamSubscriber;
    private final ObjectMapper objectMapper;
    private final ReviewAgentService reviewAgentService;

    public ReviewAgentListener(JetStreamSubscriber jetStreamSubscriber,
                                ObjectMapper objectMapper,
                                ReviewAgentService reviewAgentService) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.reviewAgentService = reviewAgentService;
    }

    @PostConstruct
    public void subscribe() {
        jetStreamSubscriber.subscribe(STATE_CHANGED_SUBJECT, DURABLE_NAME, QUEUE_GROUP, this::handleMessage);
        log.info("Subscribed to {} for review agent triggers (durable={}, queue={})",
                STATE_CHANGED_SUBJECT, DURABLE_NAME, QUEUE_GROUP);
    }

    void handleMessage(Message message) {
        try {
            byte[] data = message.getData();
            if (data == null || data.length == 0) {
                log.warn("Received message with null or empty data, ignoring");
                return;
            }

            String json = new String(data, StandardCharsets.UTF_8);
            TaskStateChangedEvent event = objectMapper.readValue(json, TaskStateChangedEvent.class);

            if ("REVIEW".equals(event.getToState())) {
                log.info("Task {} transitioned to REVIEW, triggering review agent", event.getTaskId());
                reviewAgentService.executeReview(event);
            }
        } catch (Exception e) {
            log.error("Failed to handle state changed event", e);
        }
    }
}
