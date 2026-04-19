package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.AgentTestRequest;
import com.squadron.agent.dto.AgentTestResult;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTestExecutionServiceTest {

    @Mock
    private UserAgentConfigRepository agentConfigRepository;

    @Mock
    private AgentTestConfigService testConfigService;

    @Mock
    private TestDataGeneratorService testDataGenerator;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private AgentProvider mockProvider;

    private AgentTestExecutionService service;

    private UUID tenantId;
    private UUID userId;
    private UUID agentConfigId;
    private UserAgentConfig agentConfig;

    @BeforeEach
    void setUp() {
        service = new AgentTestExecutionService(agentConfigRepository, testConfigService,
                testDataGenerator, providerRegistry);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        agentConfigId = UUID.randomUUID();

        agentConfig = UserAgentConfig.builder()
                .id(agentConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Sol")
                .agentType("GENERAL")
                .provider("ollama")
                .model("gemma4:e2b")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();
    }

    private void setupHappyPath(String testMode) {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        AgentTestConfig testConfig = AgentTestConfig.builder()
                .tenantId(tenantId)
                .userId(userId)
                .generatorProvider("ollama")
                .generatorModel("gemma4:e2b")
                .generatorHostingType("SELF_HOSTED")
                .build();
        when(testConfigService.getOrCreateConfig(tenantId, userId)).thenReturn(testConfig);

        String fakeData = "fake test data for " + testMode;
        switch (testMode) {
            case "PLANNING" -> when(testDataGenerator.generateFakePlan(any())).thenReturn(fakeData);
            case "CODE_GENERATION" -> when(testDataGenerator.generateFakeCodebase(any())).thenReturn(fakeData);
            case "CODE_REVIEW" -> when(testDataGenerator.generateFakeCodeForReview(any())).thenReturn(fakeData);
        }

        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("Agent response for " + testMode);
    }

    @Test
    void should_executeTest_when_planningMode() {
        setupHappyPath("PLANNING");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("PLANNING", result.getTestMode());
        assertNotNull(result.getAgentOutput());
        assertTrue(result.getSummary().contains("Sol"));
        assertTrue(result.getSummary().contains("PLANNING"));
    }

    @Test
    void should_executeTest_when_codeGenerationMode() {
        setupHappyPath("CODE_GENERATION");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("CODE_GENERATION")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("CODE_GENERATION", result.getTestMode());
    }

    @Test
    void should_executeTest_when_codeReviewMode() {
        setupHappyPath("CODE_REVIEW");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("CODE_REVIEW")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("CODE_REVIEW", result.getTestMode());
    }

    @Test
    void should_returnFailure_when_agentConfigNotFound() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.empty());

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getSummary().contains("failed"));
    }

    @Test
    void should_returnFailure_when_agentProviderThrows() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        AgentTestConfig testConfig = AgentTestConfig.builder()
                .tenantId(tenantId).userId(userId).build();
        when(testConfigService.getOrCreateConfig(tenantId, userId)).thenReturn(testConfig);
        when(testDataGenerator.generateFakePlan(any())).thenReturn("fake plan data");
        when(providerRegistry.getProvider(anyString())).thenThrow(new RuntimeException("Provider unavailable"));

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getSummary().contains("Provider unavailable"));
    }

    @Test
    void should_returnFailure_when_invalidTestMode() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        AgentTestConfig testConfig = AgentTestConfig.builder()
                .tenantId(tenantId).userId(userId).build();
        when(testConfigService.getOrCreateConfig(tenantId, userId)).thenReturn(testConfig);

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("INVALID_MODE")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getSummary().contains("Unknown test mode"));
    }

    @Test
    void should_includeLogEntries_when_testSucceeds() {
        setupHappyPath("PLANNING");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertFalse(result.getLogEntries().isEmpty());
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "INIT".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "CONTAINER_INIT".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "CONTAINER_READY".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "CONTAINER_CLEANUP".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "COMPLETE".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "SUCCESS".equals(e.getLevel())));
        // Verify container lifecycle messages
        assertTrue(result.getLogEntries().stream().anyMatch(e ->
                e.getMessage().contains("ephemeral sandbox container")));
        assertTrue(result.getLogEntries().stream().anyMatch(e ->
                e.getMessage().contains("Removing ephemeral container")));
    }

    @Test
    void should_includeContainerCleanupLog_when_testFails() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        AgentTestConfig testConfig = AgentTestConfig.builder()
                .tenantId(tenantId).userId(userId).build();
        when(testConfigService.getOrCreateConfig(tenantId, userId)).thenReturn(testConfig);
        when(testDataGenerator.generateFakePlan(any())).thenThrow(new RuntimeException("Generator error"));

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("FAILURE", result.getStatus());
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "CONTAINER_CLEANUP".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e ->
                e.getMessage().contains("Cleaning up ephemeral sandbox")));
    }

    @Test
    void should_includeErrorLog_when_testFails() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.empty());

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertTrue(result.getLogEntries().stream().anyMatch(e -> "ERROR".equals(e.getPhase())));
        assertTrue(result.getLogEntries().stream().anyMatch(e -> "ERROR".equals(e.getLevel())));
    }

    @Test
    void should_verifyOwnership_when_agentConfigBelongsToDifferentUser() {
        UUID otherUserId = UUID.randomUUID();
        UserAgentConfig otherConfig = UserAgentConfig.builder()
                .id(agentConfigId)
                .tenantId(tenantId)
                .userId(otherUserId)
                .agentName("Other's Agent")
                .provider("ollama")
                .model("gemma4:e2b")
                .build();

        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(otherConfig));

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        AgentTestResult result = service.executeTest(tenantId, userId, request);

        assertEquals("FAILURE", result.getStatus());
    }

    // =================== Streaming Tests ===================

    @Test
    void should_streamProgressEvents_when_testSucceeds() {
        setupHappyPath("PLANNING");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        var flux = service.executeTestStreaming(tenantId, userId, request);
        List<ServerSentEvent<AgentTestResult>> events = new ArrayList<>();

        StepVerifier.create(flux)
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        // Should have multiple progress events + 1 final complete event
        assertFalse(events.isEmpty());
        // Last event should be "complete" with SUCCESS status
        var lastEvent = events.get(events.size() - 1);
        assertEquals("complete", lastEvent.event());
        assertNotNull(lastEvent.data());
        assertEquals("SUCCESS", lastEvent.data().getStatus());
        assertNotNull(lastEvent.data().getAgentOutput());
        assertTrue(lastEvent.data().getSummary().contains("Sol"));
    }

    @Test
    void should_streamProgressAndFailure_when_testFails() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.empty());

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        var flux = service.executeTestStreaming(tenantId, userId, request);
        List<ServerSentEvent<AgentTestResult>> events = new ArrayList<>();

        StepVerifier.create(flux)
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        var lastEvent = events.get(events.size() - 1);
        assertEquals("complete", lastEvent.event());
        assertEquals("FAILURE", lastEvent.data().getStatus());
        assertTrue(lastEvent.data().getSummary().contains("failed"));
    }

    @Test
    void should_emitProgressEventsWithRunningStatus_when_streaming() {
        setupHappyPath("CODE_GENERATION");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("CODE_GENERATION")
                .build();

        var flux = service.executeTestStreaming(tenantId, userId, request);
        List<ServerSentEvent<AgentTestResult>> events = new ArrayList<>();

        StepVerifier.create(flux)
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        // All intermediate events should have "progress" event type and RUNNING status
        for (int i = 0; i < events.size() - 1; i++) {
            assertEquals("progress", events.get(i).event());
            assertEquals("RUNNING", events.get(i).data().getStatus());
        }
        // Last event should be complete
        assertEquals("complete", events.get(events.size() - 1).event());
    }

    @Test
    void should_includeGrowingLogEntries_when_streaming() {
        setupHappyPath("CODE_REVIEW");

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("CODE_REVIEW")
                .build();

        var flux = service.executeTestStreaming(tenantId, userId, request);
        List<ServerSentEvent<AgentTestResult>> events = new ArrayList<>();

        StepVerifier.create(flux)
                .recordWith(() -> events)
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        // Each progress event should have more log entries than the previous
        int previousCount = 0;
        for (var event : events) {
            int currentCount = event.data().getLogEntries().size();
            assertTrue(currentCount >= previousCount,
                    "Log entries should grow: " + currentCount + " < " + previousCount);
            previousCount = currentCount;
        }
    }
}
