package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.PlanService;
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

@Component
public class PlanningAgentListener {

    private static final Logger log = LoggerFactory.getLogger(PlanningAgentListener.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.orchestrator.task.state-changed";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final PlanService planService;
    private Dispatcher dispatcher;

    public PlanningAgentListener(Connection natsConnection,
                                  ObjectMapper objectMapper,
                                  AgentService agentService,
                                  PlanService planService) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.planService = planService;
    }

    @PostConstruct
    public void subscribe() {
        dispatcher = natsConnection.createDispatcher(this::handleMessage);
        dispatcher.subscribe(STATE_CHANGED_SUBJECT);
        log.info("Subscribed to {} for planning agent triggers", STATE_CHANGED_SUBJECT);
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
            String json = new String(message.getData(), StandardCharsets.UTF_8);
            TaskStateChangedEvent event = objectMapper.readValue(json, TaskStateChangedEvent.class);

            if ("PLANNING".equals(event.getToState())) {
                log.info("Task {} transitioned to PLANNING, triggering planning agent", event.getTaskId());
                triggerPlanningAgent(event);
            }
        } catch (Exception e) {
            log.error("Failed to handle state changed event", e);
        }
    }

    private void triggerPlanningAgent(TaskStateChangedEvent event) {
        try {
            // Build a planning request
            ChatRequest request = ChatRequest.builder()
                    .taskId(event.getTaskId())
                    .agentType("PLANNING")
                    .message("Please analyze this task and create a detailed implementation plan.")
                    .build();

            // Call the agent service to generate a plan
            ChatResponse response = agentService.chat(request, event.getTenantId(), event.getTriggeredBy());

            // Save as a draft plan
            if (response != null && response.getContent() != null) {
                TaskPlan plan = planService.createPlan(
                        event.getTenantId(),
                        event.getTaskId(),
                        response.getConversationId(),
                        response.getContent());
                log.info("Created draft plan v{} for task {} (plan id={})",
                        plan.getVersion(), event.getTaskId(), plan.getId());
            }
        } catch (Exception e) {
            log.error("Failed to trigger planning agent for task {}", event.getTaskId(), e);
        }
    }
}
