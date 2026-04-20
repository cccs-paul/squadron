package com.squadron.agent.service;

import com.squadron.agent.client.ResilientOrchestratorClient;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.event.TicketlessTaskCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles ticketless task execution. When a ticketless task is created,
 * this service resolves the agent config, invokes the appropriate agent
 * (PLAN or BUILD), and updates the task status via the orchestrator.
 */
@Service
public class TicketlessTaskService {

    private static final Logger log = LoggerFactory.getLogger(TicketlessTaskService.class);

    private final AgentService agentService;
    private final UserAgentConfigRepository agentConfigRepository;
    private final ResilientOrchestratorClient orchestratorClient;

    public TicketlessTaskService(AgentService agentService,
                                  UserAgentConfigRepository agentConfigRepository,
                                  ResilientOrchestratorClient orchestratorClient) {
        this.agentService = agentService;
        this.agentConfigRepository = agentConfigRepository;
        this.orchestratorClient = orchestratorClient;
    }

    /**
     * Executes a ticketless task: resolves the agent config, runs the agent
     * in the requested mode (PLAN or BUILD), and updates the status.
     */
    public void execute(TicketlessTaskCreatedEvent event) {
        UUID taskId = event.getTaskId();
        UUID tenantId = event.getTenantId();
        String mode = event.getAgentMode();

        log.info("Executing ticketless task {} in {} mode (agent config: {})",
                taskId, mode, event.getAgentConfigId());

        try {
            // Update status to PLANNING or BUILDING
            String activeStatus = "PLAN".equalsIgnoreCase(mode) ? "PLANNING" : "BUILDING";
            updateStatus(taskId, activeStatus);

            // Resolve agent config
            Optional<UserAgentConfig> agentConfigOpt = agentConfigRepository
                    .findByIdAndTenantId(event.getAgentConfigId(), tenantId);

            if (agentConfigOpt.isEmpty()) {
                log.error("Agent config {} not found for tenant {}", event.getAgentConfigId(), tenantId);
                updateStatus(taskId, "FAILED");
                return;
            }

            UserAgentConfig agentConfig = agentConfigOpt.get();

            // Build the agent type from the config
            String agentType = agentConfig.getAgentType();
            if (agentType == null || agentType.isBlank()) {
                agentType = "PLAN".equalsIgnoreCase(mode) ? "PLANNING" : "CODING";
            }

            // Build the chat request with the user's prompt
            ChatRequest chatRequest = ChatRequest.builder()
                    .taskId(taskId)
                    .agentType(agentType)
                    .message(buildPromptMessage(event))
                    .build();

            // Execute using AgentService (reuses conversation management, config resolution, provider dispatch)
            ChatResponse response = agentService.chat(chatRequest, tenantId, agentConfig.getUserId());

            if (response != null && response.getContent() != null) {
                log.info("Ticketless task {} completed successfully (conversation: {})",
                        taskId, response.getConversationId());
                updateStatus(taskId, "COMPLETED");
            } else {
                log.warn("Ticketless task {} returned null response", taskId);
                updateStatus(taskId, "FAILED");
            }

        } catch (Exception e) {
            log.error("Ticketless task {} execution failed", taskId, e);
            updateStatus(taskId, "FAILED");
        }
    }

    private String buildPromptMessage(TicketlessTaskCreatedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.getPrompt());

        if (event.getBranchName() != null) {
            sb.append("\n\nTarget branch: ").append(event.getBranchName());
            if (event.isCreateBranch()) {
                sb.append(" (create new branch)");
            }
        }

        return sb.toString();
    }

    private void updateStatus(UUID taskId, String status) {
        try {
            orchestratorClient.updateTicketlessStatus(
                    taskId.toString(),
                    Map.of("status", status));
        } catch (Exception e) {
            log.warn("Failed to update ticketless status for task {} to {}: {}",
                    taskId, status, e.getMessage());
        }
    }
}
