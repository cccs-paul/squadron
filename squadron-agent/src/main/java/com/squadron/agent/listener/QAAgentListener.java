package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.service.QAAgentService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listens for task state-change events on NATS and triggers the QA agent
 * when a task transitions to the {@code QA} state.
 * Uses JetStream durable subscriptions for at-least-once delivery.
 */
@Component
public class QAAgentListener {

    private static final Logger log = LoggerFactory.getLogger(QAAgentListener.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.tasks.state-changed";
    static final String DURABLE_NAME = "qa-agent-listener";
    static final String QUEUE_GROUP = "squadron-agent";

    private final JetStreamSubscriber jetStreamSubscriber;
    private final ObjectMapper objectMapper;
    private final QAAgentService qaAgentService;

    public QAAgentListener(JetStreamSubscriber jetStreamSubscriber,
                           ObjectMapper objectMapper,
                           QAAgentService qaAgentService) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.qaAgentService = qaAgentService;
    }

    @PostConstruct
    public void subscribe() {
        jetStreamSubscriber.subscribe(STATE_CHANGED_SUBJECT, DURABLE_NAME, QUEUE_GROUP, this::handleMessage);
        log.info("Subscribed to {} for QA agent triggers (durable={}, queue={})",
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

            if ("QA".equals(event.getToState())) {
                log.info("Task {} transitioned to QA, triggering QA agent", event.getTaskId());
                qaAgentService.executeQA(event);
            }
        } catch (Exception e) {
            log.error("Failed to handle state changed event for QA agent", e);
        }
    }
}
