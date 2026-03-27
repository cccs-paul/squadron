package com.squadron.platform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.ApiResponse;
import com.squadron.platform.dto.WebhookPayload;
import com.squadron.platform.service.WebhookProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/platforms/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookProcessingService webhookProcessingService;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookProcessingService webhookProcessingService, ObjectMapper objectMapper) {
        this.webhookProcessingService = webhookProcessingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/jira")
    public ResponseEntity<ApiResponse<Void>> receiveJiraWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature", required = false) String signatureHeader) {
        try {
            Map<String, Object> payload = parsePayload(rawBody);
            log.info("Received JIRA webhook: {}", payload.getOrDefault("webhookEvent", "unknown"));
            webhookProcessingService.processWebhook("JIRA", payload, signatureHeader, rawBody);
        } catch (Exception e) {
            log.error("Failed to process JIRA webhook", e);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<Void>> receiveGitHubWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) {
        try {
            Map<String, Object> payload = parsePayload(rawBody);
            log.info("Received GitHub webhook: {}", payload.getOrDefault("action", "unknown"));
            webhookProcessingService.processWebhook("GITHUB", payload, signatureHeader, rawBody);
        } catch (Exception e) {
            log.error("Failed to process GitHub webhook", e);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/gitlab")
    public ResponseEntity<ApiResponse<Void>> receiveGitLabWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String signatureHeader) {
        try {
            Map<String, Object> payload = parsePayload(rawBody);
            log.info("Received GitLab webhook: {}", payload.getOrDefault("object_kind", "unknown"));
            webhookProcessingService.processWebhook("GITLAB", payload, signatureHeader, rawBody);
        } catch (Exception e) {
            log.error("Failed to process GitLab webhook", e);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/azuredevops")
    public ResponseEntity<ApiResponse<Void>> receiveAzureDevOpsWebhook(
            @RequestBody byte[] rawBody) {
        try {
            Map<String, Object> payload = parsePayload(rawBody);
            log.info("Received Azure DevOps webhook: {}", payload.getOrDefault("eventType", "unknown"));
            webhookProcessingService.processWebhook("AZURE_DEVOPS", payload, null, rawBody);
        } catch (Exception e) {
            log.error("Failed to process Azure DevOps webhook", e);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Map<String, Object> parsePayload(byte[] rawBody) throws Exception {
        return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
    }
}
