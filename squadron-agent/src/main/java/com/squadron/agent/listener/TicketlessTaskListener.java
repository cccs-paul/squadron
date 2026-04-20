package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.service.TicketlessTaskService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TicketlessTaskCreatedEvent;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Listens on {@code squadron.tasks.ticketless.created} for ticketless task events
 * and delegates execution to {@link TicketlessTaskService}.
 */
@Component
public class TicketlessTaskListener {

    private static final Logger log = LoggerFactory.getLogger(TicketlessTaskListener.class);
    static final String SUBJECT = "squadron.tasks.ticketless.created";
    static final String DURABLE_NAME = "ticketless-task-listener";
    static final String QUEUE_GROUP = "squadron-agent";

    private final JetStreamSubscriber jetStreamSubscriber;
    private final ObjectMapper objectMapper;
    private final TicketlessTaskService ticketlessTaskService;

    public TicketlessTaskListener(JetStreamSubscriber jetStreamSubscriber,
                                   ObjectMapper objectMapper,
                                   TicketlessTaskService ticketlessTaskService) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.ticketlessTaskService = ticketlessTaskService;
    }

    @PostConstruct
    public void subscribe() {
        jetStreamSubscriber.subscribe(SUBJECT, DURABLE_NAME, QUEUE_GROUP, this::handleMessage);
        log.info("Subscribed to {} for ticketless task execution (durable={}, queue={})",
                SUBJECT, DURABLE_NAME, QUEUE_GROUP);
    }

    void handleMessage(Message message) {
        try {
            byte[] data = message.getData();
            if (data == null || data.length == 0) {
                log.warn("Received ticketless task message with null or empty data, ignoring");
                return;
            }

            String json = new String(data, StandardCharsets.UTF_8);
            TicketlessTaskCreatedEvent event = objectMapper.readValue(json, TicketlessTaskCreatedEvent.class);

            log.info("Received ticketless task created event for task {} (mode: {})",
                    event.getTaskId(), event.getAgentMode());

            ticketlessTaskService.execute(event);

        } catch (Exception e) {
            log.error("Failed to handle ticketless task created event", e);
        }
    }
}
