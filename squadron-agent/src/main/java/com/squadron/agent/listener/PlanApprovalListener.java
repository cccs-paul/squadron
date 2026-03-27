package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.AgentCompletedEvent;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Listens for plan approval events on NATS and triggers a state transition
 * from PLANNING to PROPOSE_CODE in the orchestrator.
 * Uses JetStream durable subscriptions for at-least-once delivery.
 */
@Component
public class PlanApprovalListener {

    private static final Logger log = LoggerFactory.getLogger(PlanApprovalListener.class);
    static final String PLAN_APPROVED_SUBJECT = "squadron.agent.plan.approved";
    static final String DURABLE_NAME = "plan-approval-listener";
    static final String QUEUE_GROUP = "squadron-agent";

    private final JetStreamSubscriber jetStreamSubscriber;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Autowired
    public PlanApprovalListener(JetStreamSubscriber jetStreamSubscriber,
                                 ObjectMapper objectMapper,
                                 @Value("${squadron.orchestrator.url:http://localhost:8083}") String orchestratorUrl) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(orchestratorUrl)
                .build();
    }

    // Visible for testing
    PlanApprovalListener(JetStreamSubscriber jetStreamSubscriber, ObjectMapper objectMapper, WebClient webClient) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    @PostConstruct
    public void subscribe() {
        jetStreamSubscriber.subscribe(PLAN_APPROVED_SUBJECT, DURABLE_NAME, QUEUE_GROUP, this::handleMessage);
        log.info("Subscribed to {} for plan approval state transitions (durable={}, queue={})",
                PLAN_APPROVED_SUBJECT, DURABLE_NAME, QUEUE_GROUP);
    }

    void handleMessage(Message message) {
        try {
            String json = new String(message.getData(), StandardCharsets.UTF_8);
            AgentCompletedEvent event = objectMapper.readValue(json, AgentCompletedEvent.class);

            if ("PLANNING".equals(event.getAgentType()) && event.isSuccess()) {
                log.info("Plan approved for task {}, transitioning to PROPOSE_CODE", event.getTaskId());
                transitionToProposeCode(event);
            }
        } catch (Exception e) {
            log.error("Failed to handle plan approval event", e);
        }
    }

    private void transitionToProposeCode(AgentCompletedEvent event) {
        try {
            Map<String, Object> body = Map.of(
                    "targetState", "PROPOSE_CODE",
                    "reason", "Plan approved by user"
            );

            webClient.post()
                    .uri("/api/tasks/{taskId}/transition", event.getTaskId())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(r -> log.info("Task {} transitioned to PROPOSE_CODE", event.getTaskId()))
                    .doOnError(e -> log.error("Failed to transition task {} to PROPOSE_CODE", event.getTaskId(), e))
                    .subscribe();
        } catch (Exception e) {
            log.error("Failed to call orchestrator for task {} transition", event.getTaskId(), e);
        }
    }
}
