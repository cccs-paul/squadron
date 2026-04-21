package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentTestConfigDto;
import com.squadron.agent.dto.AgentTestRequest;
import com.squadron.agent.dto.AgentTestResult;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.service.AgentTestConfigService;
import com.squadron.agent.service.AgentTestExecutionService;
import com.squadron.agent.service.InteractiveTestSessionService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for agent testing.
 * Provides endpoints to:
 * <ul>
 *   <li>Execute automated agent tests (planning, code generation, code review)</li>
 *   <li>Manage the test data generator configuration</li>
 *   <li>Start and manage interactive test sessions (multi-turn chat)</li>
 * </ul>
 *
 * Path: /api/agents/test (forwarded from gateway without stripPrefix).
 */
@RestController
@RequestMapping("/api/agents/test")
public class AgentTestController {

    private static final Logger log = LoggerFactory.getLogger(AgentTestController.class);

    private final AgentTestExecutionService executionService;
    private final AgentTestConfigService configService;
    private final InteractiveTestSessionService interactiveService;

    public AgentTestController(AgentTestExecutionService executionService,
                                AgentTestConfigService configService,
                                InteractiveTestSessionService interactiveService) {
        this.executionService = executionService;
        this.configService = configService;
        this.interactiveService = interactiveService;
    }

    // ========================= Automated Tests =========================

    /**
     * Executes an agent test synchronously and returns the full result.
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<AgentTestResult>> executeTest(
            @Valid @RequestBody AgentTestRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        log.info("Agent test requested: agentConfigId={}, mode={}, user={}",
                request.getAgentConfigId(), request.getTestMode(), userId);

        AgentTestResult result = executionService.executeTest(tenantId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Executes an agent test and streams progress via Server-Sent Events.
     */
    @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentTestResult>> executeTestStream(
            @Valid @RequestBody AgentTestRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        log.info("Agent test stream requested: agentConfigId={}, mode={}, user={}",
                request.getAgentConfigId(), request.getTestMode(), userId);

        return executionService.executeTestStreaming(tenantId, userId, request);
    }

    // ========================= Test Config =========================

    /**
     * Returns the user's test data generator configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AgentTestConfigDto>> getTestConfig() {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        AgentTestConfig config = configService.getOrCreateConfig(tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(configService.toDto(config)));
    }

    /**
     * Updates the user's test data generator configuration.
     */
    @PutMapping("/config")
    public ResponseEntity<ApiResponse<AgentTestConfigDto>> updateTestConfig(
            @Valid @RequestBody AgentTestConfigDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        AgentTestConfig config = configService.updateConfig(tenantId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success(configService.toDto(config)));
    }

    // ========================= Interactive Test Sessions =========================

    /**
     * Starts a new interactive test session for the specified agent.
     * Interactive sessions allow multi-turn, free-form conversation with an agent
     * without requiring a real task, workspace, or project.
     */
    @PostMapping("/interactive/start")
    public ResponseEntity<ApiResponse<InteractiveTestSessionDto>> startInteractiveSession(
            @RequestParam UUID agentConfigId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        log.info("Interactive test session requested: agentConfigId={}, user={}", agentConfigId, userId);

        InteractiveTestSessionDto session = interactiveService.startSession(tenantId, userId, agentConfigId);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * Sends a message in an interactive test session and streams the agent's response via SSE.
     * Each SSE event contains a full session snapshot including all messages so far,
     * allowing the UI to progressively update.
     */
    @PostMapping(value = "/interactive/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<InteractiveTestSessionDto>> sendInteractiveMessage(
            @Valid @RequestBody InteractiveTestMessageRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        log.info("Interactive test message: sessionId={}, user={}", request.getSessionId(), userId);

        return interactiveService.sendMessage(tenantId, userId, request);
    }

    /**
     * Returns the current state of an interactive test session.
     */
    @GetMapping("/interactive/{sessionId}")
    public ResponseEntity<ApiResponse<InteractiveTestSessionDto>> getInteractiveSession(
            @PathVariable UUID sessionId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        InteractiveTestSessionDto session = interactiveService.getSession(sessionId, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * Lists all active interactive test sessions for the current user.
     */
    @GetMapping("/interactive/sessions")
    public ResponseEntity<ApiResponse<List<InteractiveTestSessionDto>>> listInteractiveSessions() {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        List<InteractiveTestSessionDto> sessions = interactiveService.getUserSessions(tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    /**
     * Closes an interactive test session and frees resources.
     */
    @DeleteMapping("/interactive/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> closeInteractiveSession(
            @PathVariable UUID sessionId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        log.info("Closing interactive test session: sessionId={}, user={}", sessionId, userId);

        interactiveService.closeSession(sessionId, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
