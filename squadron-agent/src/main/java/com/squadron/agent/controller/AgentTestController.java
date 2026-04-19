package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentTestConfigDto;
import com.squadron.agent.dto.AgentTestRequest;
import com.squadron.agent.dto.AgentTestResult;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.service.AgentTestConfigService;
import com.squadron.agent.service.AgentTestExecutionService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for agent testing.
 * Provides endpoints to:
 * <ul>
 *   <li>Execute agent tests (planning, code generation, code review)</li>
 *   <li>Manage the test data generator configuration</li>
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

    public AgentTestController(AgentTestExecutionService executionService,
                                AgentTestConfigService configService) {
        this.executionService = executionService;
        this.configService = configService;
    }

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
}
