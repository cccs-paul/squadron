package com.squadron.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.dto.WebhookPayload;
import com.squadron.platform.service.WebhookProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for WebhookController.
 * Webhook endpoints are under /api/platforms/webhooks/** which is permitAll (no auth required).
 */
@WebMvcTest(controllers = WebhookController.class)
@ContextConfiguration(classes = {WebhookController.class, SecurityConfig.class})
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookProcessingService webhookProcessingService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // --- POST /api/platforms/webhooks/jira ---

    @Test
    void should_receiveJiraWebhook_when_validPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "webhookEvent", "jira:issue_updated",
                "issue", Map.of("key", "PROJ-123")
        );

        when(webhookProcessingService.processWebhook(eq("JIRA"), any(), any(), any()))
                .thenReturn(buildDefaultPayload("JIRA"));

        mockMvc.perform(post("/api/platforms/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("JIRA"), any(), isNull(), any());
    }

    @Test
    void should_receiveJiraWebhook_when_noWebhookEvent() throws Exception {
        Map<String, Object> payload = Map.of("issue", Map.of("key", "PROJ-456"));

        when(webhookProcessingService.processWebhook(eq("JIRA"), any(), any(), any()))
                .thenReturn(buildDefaultPayload("JIRA"));

        mockMvc.perform(post("/api/platforms/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void should_handleJiraWebhook_when_processingFails() throws Exception {
        Map<String, Object> payload = Map.of("webhookEvent", "jira:issue_created");

        when(webhookProcessingService.processWebhook(eq("JIRA"), any(), any(), any()))
                .thenThrow(new RuntimeException("Processing failed"));

        // The controller catches exceptions, so the response should still be 200.
        mockMvc.perform(post("/api/platforms/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // --- POST /api/platforms/webhooks/github ---

    @Test
    void should_receiveGitHubWebhook_when_validPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "action", "opened",
                "issue", Map.of("number", 42)
        );

        when(webhookProcessingService.processWebhook(eq("GITHUB"), any(), any(), any()))
                .thenReturn(buildDefaultPayload("GITHUB"));

        mockMvc.perform(post("/api/platforms/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("GITHUB"), any(), isNull(), any());
    }

    @Test
    void should_receiveGitHubWebhook_when_noAction() throws Exception {
        Map<String, Object> payload = Map.of("repository", Map.of("full_name", "org/repo"));

        when(webhookProcessingService.processWebhook(eq("GITHUB"), any(), any(), any()))
                .thenReturn(buildDefaultPayload("GITHUB"));

        mockMvc.perform(post("/api/platforms/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // --- POST /api/platforms/webhooks/gitlab ---

    @Test
    void should_receiveGitLabWebhook_when_validPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "object_kind", "merge_request",
                "project", Map.of("id", 1)
        );

        when(webhookProcessingService.processWebhook(eq("GITLAB"), any(), any(), any()))
                .thenReturn(buildDefaultPayload("GITLAB"));

        mockMvc.perform(post("/api/platforms/webhooks/gitlab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("GITLAB"), any(), isNull(), any());
    }

    // --- POST /api/platforms/webhooks/azuredevops ---

    @Test
    void should_receiveAzureDevOpsWebhook_when_validPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventType", "workitem.updated",
                "resource", Map.of("id", 100)
        );

        when(webhookProcessingService.processWebhook(eq("AZURE_DEVOPS"), any(), isNull(), any()))
                .thenReturn(buildDefaultPayload("AZURE_DEVOPS"));

        mockMvc.perform(post("/api/platforms/webhooks/azuredevops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("AZURE_DEVOPS"), any(), isNull(), any());
    }

    @Test
    void should_handleAzureDevOpsWebhook_when_processingFails() throws Exception {
        Map<String, Object> payload = Map.of("eventType", "workitem.created");

        when(webhookProcessingService.processWebhook(eq("AZURE_DEVOPS"), any(), isNull(), any()))
                .thenThrow(new RuntimeException("Processing failed"));

        mockMvc.perform(post("/api/platforms/webhooks/azuredevops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // --- No auth required for webhooks (permitAll) ---

    @Test
    void should_allowUnauthenticatedAccess_toAllWebhookEndpoints() throws Exception {
        when(webhookProcessingService.processWebhook(any(), any(), any(), any()))
                .thenReturn(buildDefaultPayload("TEST"));

        String payload = objectMapper.writeValueAsString(Map.of("event", "test"));

        // All webhook endpoints should return 200 even without authentication
        mockMvc.perform(post("/api/platforms/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platforms/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platforms/webhooks/gitlab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/platforms/webhooks/azuredevops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    // --- Signature header passing ---

    @Test
    void should_passGitHubSignatureHeader_when_present() throws Exception {
        Map<String, Object> payload = Map.of("action", "opened");
        String signatureHeader = "sha256=abcdef1234567890";

        when(webhookProcessingService.processWebhook(eq("GITHUB"), any(), eq(signatureHeader), any()))
                .thenReturn(buildDefaultPayload("GITHUB"));

        mockMvc.perform(post("/api/platforms/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signatureHeader)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("GITHUB"), any(), eq(signatureHeader), any());
    }

    @Test
    void should_passJiraSignatureHeader_when_present() throws Exception {
        Map<String, Object> payload = Map.of("webhookEvent", "jira:issue_updated");
        String signatureHeader = "sha256=jira-hmac-signature";

        when(webhookProcessingService.processWebhook(eq("JIRA"), any(), eq(signatureHeader), any()))
                .thenReturn(buildDefaultPayload("JIRA"));

        mockMvc.perform(post("/api/platforms/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", signatureHeader)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("JIRA"), any(), eq(signatureHeader), any());
    }

    @Test
    void should_passGitLabTokenHeader_when_present() throws Exception {
        Map<String, Object> payload = Map.of("object_kind", "issue");
        String tokenHeader = "my-gitlab-secret-token";

        when(webhookProcessingService.processWebhook(eq("GITLAB"), any(), eq(tokenHeader), any()))
                .thenReturn(buildDefaultPayload("GITLAB"));

        mockMvc.perform(post("/api/platforms/webhooks/gitlab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Gitlab-Token", tokenHeader)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookProcessingService).processWebhook(eq("GITLAB"), any(), eq(tokenHeader), any());
    }

    // --- Helper ---

    private WebhookPayload buildDefaultPayload(String platform) {
        return WebhookPayload.builder()
                .platform(platform)
                .eventType("test")
                .receivedAt(Instant.now())
                .build();
    }
}
