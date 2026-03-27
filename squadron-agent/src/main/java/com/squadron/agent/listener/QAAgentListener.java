package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.service.QAAgentService;
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
 * Listens for task state-change events on NATS and triggers the QA agent
 * when a task transitions to the {@code QA} state.
 */
@Component
public class QAAgentListener {

    private static final Logger log = LoggerFactory.getLogger(QAAgentListener.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.orchestrator.task.state-changed";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final QAAgentService qaAgentService;
    private Dispatcher dispatcher;

    public QAAgentListener(Connection natsConnection,
                           ObjectMapper objectMapper,
                           QAAgentService qaAgentService) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.qaAgentService = qaAgentService;
    }

    @PostConstruct
    public void subscribe() {
        dispatcher = natsConnection.createDispatcher(this::handleMessage);
        dispatcher.subscribe(STATE_CHANGED_SUBJECT);
        log.info("Subscribed to {} for QA agent triggers", STATE_CHANGED_SUBJECT);
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

            if ("QA".equals(event.getToState())) {
                log.info("Task {} transitioned to QA, triggering QA agent", event.getTaskId());
                qaAgentService.executeQA(event);
            }
        } catch (Exception e) {
            log.error("Failed to handle state changed event for QA agent", e);
        }
    }
}
