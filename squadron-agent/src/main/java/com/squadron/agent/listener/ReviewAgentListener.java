package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listens for task state-change events on NATS and triggers the review agent
 * when a task transitions to the {@code REVIEW} state.
 */
@Component
public class ReviewAgentListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgentListener.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.orchestrator.task.state-changed";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final ReviewAgentService reviewAgentService;
    private Dispatcher dispatcher;

    public ReviewAgentListener(Connection natsConnection,
                                ObjectMapper objectMapper,
                                ReviewAgentService reviewAgentService) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.reviewAgentService = reviewAgentService;
    }

    @PostConstruct
    public void subscribe() {
        dispatcher = natsConnection.createDispatcher(this::handleMessage);
        dispatcher.subscribe(STATE_CHANGED_SUBJECT);
        log.info("Subscribed to {} for review agent triggers", STATE_CHANGED_SUBJECT);
    }

    @PreDestroy
    public void unsubscribe() {
        if (dispatcher != null) {
            natsConnection.closeDispatcher(dispatcher);
            log.info("Unsubscribed from {}", STATE_CHANGED_SUBJECT);
        }
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
