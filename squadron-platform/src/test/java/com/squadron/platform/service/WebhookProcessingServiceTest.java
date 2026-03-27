package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.WebhookPayload;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    @Mock
    private PlatformConnectionRepository connectionRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private WebhookSignatureValidator signatureValidator;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private ObjectMapper objectMapper;

    private WebhookProcessingService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new WebhookProcessingService(
                connectionRepository, encryptionService, signatureValidator,
                natsEventPublisher, objectMapper);
    }

    // --- JIRA ---

    @Test
    void should_processJiraWebhook_when_connectionFound() {
        PlatformConnection connection = buildConnection("JIRA_CLOUD", "encrypted-secret");
        Map<String, Object> payload = new HashMap<>();
        payload.put("webhookEvent", "jira:issue_updated");
        payload.put("issue", Map.of("key", "PROJ-123"));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeInAndStatus(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(connection));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("decrypted-secret");
        when(signatureValidator.validateSignature(eq("JIRA"), eq("decrypted-secret"), eq("sha256=abc"), eq(rawBody)))
                .thenReturn(true);
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("JIRA", payload, "sha256=abc", rawBody);

        assertThat(result.getPlatform()).isEqualTo("JIRA");
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getAction()).isEqualTo("jira:issue_updated");
        assertThat(result.getExternalId()).isEqualTo("PROJ-123");
        verify(natsEventPublisher).publish(eq("platform.webhooks.jira"), any(SquadronEvent.class));
    }

    // --- GitHub ---

    @Test
    void should_processGitHubWebhook_when_connectionFound() {
        PlatformConnection connection = buildConnection("GITHUB", "encrypted-secret");
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        payload.put("issue", Map.of("number", 42));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(List.of(connection));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("decrypted-secret");
        when(signatureValidator.validateSignature(eq("GITHUB"), eq("decrypted-secret"), eq("sha256=xyz"), eq(rawBody)))
                .thenReturn(true);
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("GITHUB", payload, "sha256=xyz", rawBody);

        assertThat(result.getPlatform()).isEqualTo("GITHUB");
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getAction()).isEqualTo("opened");
        assertThat(result.getExternalId()).isEqualTo("42");
        verify(natsEventPublisher).publish(eq("platform.webhooks.github"), any(SquadronEvent.class));
    }

    // --- GitLab ---

    @Test
    void should_processGitLabWebhook_when_connectionFound() {
        PlatformConnection connection = buildConnection("GITLAB", "encrypted-secret");
        Map<String, Object> payload = new HashMap<>();
        payload.put("object_kind", "issue");
        payload.put("object_attributes", Map.of("iid", 99));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITLAB", "ACTIVE"))
                .thenReturn(List.of(connection));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("my-gitlab-token");
        when(signatureValidator.validateSignature(eq("GITLAB"), eq("my-gitlab-token"), eq("my-gitlab-token"), eq(rawBody)))
                .thenReturn(true);
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("GITLAB", payload, "my-gitlab-token", rawBody);

        assertThat(result.getPlatform()).isEqualTo("GITLAB");
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getAction()).isEqualTo("issue");
        assertThat(result.getExternalId()).isEqualTo("99");
        verify(natsEventPublisher).publish(eq("platform.webhooks.gitlab"), any(SquadronEvent.class));
    }

    // --- Azure DevOps ---

    @Test
    void should_processAzureDevOpsWebhook_when_connectionFound() {
        PlatformConnection connection = buildConnection("AZURE_DEVOPS", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "workitem.updated");
        payload.put("resource", Map.of("id", 500));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("AZURE_DEVOPS", "ACTIVE"))
                .thenReturn(List.of(connection));
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("AZURE_DEVOPS", payload, null, rawBody);

        assertThat(result.getPlatform()).isEqualTo("AZURE_DEVOPS");
        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getAction()).isEqualTo("workitem.updated");
        assertThat(result.getExternalId()).isEqualTo("500");
        verify(natsEventPublisher).publish(eq("platform.webhooks.azure_devops"), any(SquadronEvent.class));
    }

    // --- No connection found ---

    @Test
    void should_processWebhook_when_noConnectionFound() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(Collections.emptyList());
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("GITHUB", payload, "sha256=abc", rawBody);

        assertThat(result.getPlatform()).isEqualTo("GITHUB");
        assertThat(result.getTenantId()).isNull();
        assertThat(result.getConnectionId()).isNull();
        assertThat(result.isSignatureValid()).isFalse();
        assertThat(result.getAction()).isEqualTo("opened");
        verify(natsEventPublisher).publish(eq("platform.webhooks.github"), any(SquadronEvent.class));
    }

    // --- Signature invalid ---

    @Test
    void should_processWebhook_when_signatureInvalid() {
        PlatformConnection connection = buildConnection("GITHUB", "encrypted-secret");
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "closed");
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(List.of(connection));
        when(encryptionService.decrypt("encrypted-secret")).thenReturn("decrypted-secret");
        when(signatureValidator.validateSignature(eq("GITHUB"), eq("decrypted-secret"), eq("sha256=bad"), eq(rawBody)))
                .thenReturn(false);
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("GITHUB", payload, "sha256=bad", rawBody);

        assertThat(result.getPlatform()).isEqualTo("GITHUB");
        // When signature is invalid and no fallback connection without secret, signatureValid should be false
        assertThat(result.isSignatureValid()).isFalse();
        // Still processes the payload
        assertThat(result.getAction()).isEqualTo("closed");
        verify(natsEventPublisher).publish(eq("platform.webhooks.github"), any(SquadronEvent.class));
    }

    // --- External ID extraction ---

    @Test
    void should_extractExternalId_from_jiraPayload() {
        PlatformConnection connection = buildConnection("JIRA_CLOUD", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("webhookEvent", "jira:issue_created");
        payload.put("issue", Map.of("key", "SQUAD-42"));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeInAndStatus(anyList(), eq("ACTIVE")))
                .thenReturn(List.of(connection));
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("JIRA", payload, null, rawBody);

        assertThat(result.getExternalId()).isEqualTo("SQUAD-42");
    }

    @Test
    void should_extractExternalId_from_githubPayload() {
        PlatformConnection connection = buildConnection("GITHUB", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "labeled");
        payload.put("issue", Map.of("number", 7));
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(List.of(connection));
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        WebhookPayload result = service.processWebhook("GITHUB", payload, null, rawBody);

        assertThat(result.getExternalId()).isEqualTo("7");
    }

    // --- NATS publish ---

    @Test
    void should_publishNatsEvent_when_processing() {
        PlatformConnection connection = buildConnection("GITHUB", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(List.of(connection));
        doNothing().when(natsEventPublisher).publish(anyString(), any(SquadronEvent.class));

        service.processWebhook("GITHUB", payload, null, rawBody);

        ArgumentCaptor<SquadronEvent> eventCaptor = ArgumentCaptor.forClass(SquadronEvent.class);
        verify(natsEventPublisher).publish(eq("platform.webhooks.github"), eventCaptor.capture());

        SquadronEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getEventType()).isEqualTo("platform.webhook.github.opened");
        assertThat(publishedEvent.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(publishedEvent.getSource()).isEqualTo("squadron-platform");
    }

    @Test
    void should_handleNatsPublishFailure_gracefully() {
        PlatformConnection connection = buildConnection("GITHUB", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "opened");
        byte[] rawBody = toBytes(payload);

        when(connectionRepository.findByPlatformTypeAndStatus("GITHUB", "ACTIVE"))
                .thenReturn(List.of(connection));
        doThrow(new RuntimeException("NATS down")).when(natsEventPublisher)
                .publish(anyString(), any(SquadronEvent.class));

        // Should not throw — processWebhook catches NATS publish exceptions
        WebhookPayload result = service.processWebhook("GITHUB", payload, null, rawBody);

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo("GITHUB");
        assertThat(result.getAction()).isEqualTo("opened");
    }

    // --- Helpers ---

    private PlatformConnection buildConnection(String platformType, String webhookSecret) {
        return PlatformConnection.builder()
                .id(CONNECTION_ID)
                .tenantId(TENANT_ID)
                .platformType(platformType)
                .baseUrl("https://example.com")
                .authType("OAUTH2")
                .status("ACTIVE")
                .webhookSecret(webhookSecret)
                .build();
    }

    private byte[] toBytes(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
