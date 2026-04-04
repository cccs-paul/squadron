package com.squadron.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.CodingAgentService;
import com.squadron.agent.service.MergeService;
import com.squadron.agent.service.PlanService;
import com.squadron.agent.service.QAAgentService;
import com.squadron.agent.service.ReviewAgentService;
import com.squadron.common.config.JetStreamSubscriber;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Unified dispatcher for task state-change events. Replaces the individual
 * per-agent listeners (PlanningAgentListener, CodingAgentListener, etc.) with
 * a single NATS consumer that routes to the appropriate agent service based on
 * the target workflow state.
 *
 * <p>By using a single durable consumer with a shared queue group, any agent
 * instance in the squadron can pick up any task regardless of the workflow step.
 * The squadron is not dedicated to a particular step — agents are general-purpose
 * workers that handle whatever task is available.</p>
 *
 * <p>State-to-handler mapping:
 * <ul>
 *   <li>{@code PLANNING} — triggers the planning agent via {@link AgentService}</li>
 *   <li>{@code PROPOSE_CODE} — triggers the coding agent via {@link CodingAgentService}</li>
 *   <li>{@code REVIEW} — triggers the review agent via {@link ReviewAgentService}</li>
 *   <li>{@code QA} — triggers the QA agent via {@link QAAgentService}</li>
 *   <li>{@code MERGE} — triggers the merge automation via {@link MergeService}</li>
 * </ul>
 * All other states (BACKLOG, PRIORITIZED, DONE) are acknowledged but not dispatched.
 */
@Component
public class TaskStateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskStateDispatcher.class);
    static final String STATE_CHANGED_SUBJECT = "squadron.tasks.state-changed";
    static final String DURABLE_NAME = "task-state-dispatcher";
    static final String QUEUE_GROUP = "squadron-agent";

    /** Workflow states that require agent action. */
    static final Set<String> AGENT_STATES = Set.of(
            "PLANNING", "PROPOSE_CODE", "REVIEW", "QA", "MERGE");

    private final JetStreamSubscriber jetStreamSubscriber;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final PlanService planService;
    private final CodingAgentService codingAgentService;
    private final ReviewAgentService reviewAgentService;
    private final QAAgentService qaAgentService;
    private final MergeService mergeService;

    public TaskStateDispatcher(JetStreamSubscriber jetStreamSubscriber,
                               ObjectMapper objectMapper,
                               AgentService agentService,
                               PlanService planService,
                               CodingAgentService codingAgentService,
                               ReviewAgentService reviewAgentService,
                               QAAgentService qaAgentService,
                               MergeService mergeService) {
        this.jetStreamSubscriber = jetStreamSubscriber;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.planService = planService;
        this.codingAgentService = codingAgentService;
        this.reviewAgentService = reviewAgentService;
        this.qaAgentService = qaAgentService;
        this.mergeService = mergeService;
    }

    @PostConstruct
    public void subscribe() {
        jetStreamSubscriber.subscribe(STATE_CHANGED_SUBJECT, DURABLE_NAME, QUEUE_GROUP, this::handleMessage);
        log.info("Subscribed to {} for unified task dispatch (durable={}, queue={})",
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

            String toState = event.getToState();
            if (toState == null || !AGENT_STATES.contains(toState)) {
                log.debug("State {} does not require agent dispatch, ignoring", toState);
                return;
            }

            log.info("Task {} transitioned to {}, dispatching to agent handler",
                    event.getTaskId(), toState);
            dispatch(toState, event);

        } catch (Exception e) {
            log.error("Failed to handle state changed event", e);
        }
    }

    /**
     * Routes the event to the appropriate agent service based on the target state.
     */
    void dispatch(String toState, TaskStateChangedEvent event) {
        try {
            switch (toState) {
                case "PLANNING" -> triggerPlanningAgent(event);
                case "PROPOSE_CODE" -> codingAgentService.executeCodeGeneration(event);
                case "REVIEW" -> reviewAgentService.executeReview(event);
                case "QA" -> qaAgentService.executeQA(event);
                case "MERGE" -> mergeService.executeMerge(event);
                default -> log.warn("No handler registered for state {}", toState);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch task {} to {} handler",
                    event.getTaskId(), toState, e);
        }
    }

    private void triggerPlanningAgent(TaskStateChangedEvent event) {
        ChatRequest request = ChatRequest.builder()
                .taskId(event.getTaskId())
                .agentType("PLANNING")
                .message("Please analyze this task and create a detailed implementation plan.")
                .build();

        ChatResponse response = agentService.chat(request, event.getTenantId(), event.getTriggeredBy());

        if (response != null && response.getContent() != null) {
            TaskPlan plan = planService.createPlan(
                    event.getTenantId(),
                    event.getTaskId(),
                    response.getConversationId(),
                    response.getContent());
            log.info("Created draft plan v{} for task {} (plan id={})",
                    plan.getVersion(), event.getTaskId(), plan.getId());
        }
    }
}
