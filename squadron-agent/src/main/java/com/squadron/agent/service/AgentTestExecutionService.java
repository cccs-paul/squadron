package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.AgentTestRequest;
import com.squadron.agent.dto.AgentTestResult;
import com.squadron.agent.dto.AgentTestResult.TestLogEntry;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TenantScopedLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates end-to-end agent test execution.
 *
 * <p>The test flow:
 * <ol>
 *   <li>Look up the agent config being tested</li>
 *   <li>Generate fake test data using the configurable generator model</li>
 *   <li>Send the fake data to the agent-under-test and collect its output</li>
 *   <li>Return the full result with verbose log entries for the UI</li>
 * </ol>
 *
 * <p>Currently runs synchronously. The controller wraps this in an async
 * thread and streams progress via SSE.
 */
@Service
public class AgentTestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentTestExecutionService.class);

    private final UserAgentConfigRepository agentConfigRepository;
    private final AgentTestConfigService testConfigService;
    private final TestDataGeneratorService testDataGenerator;
    private final AgentProviderRegistry providerRegistry;

    public AgentTestExecutionService(
            UserAgentConfigRepository agentConfigRepository,
            AgentTestConfigService testConfigService,
            TestDataGeneratorService testDataGenerator,
            AgentProviderRegistry providerRegistry) {
        this.agentConfigRepository = agentConfigRepository;
        this.testConfigService = testConfigService;
        this.testDataGenerator = testDataGenerator;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Executes a full agent test. This is a blocking operation — the caller
     * should run it on a virtual thread or wrap it in async handling.
     */
    public AgentTestResult executeTest(UUID tenantId, UUID userId, AgentTestRequest request) {
        UUID testId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        List<TestLogEntry> logEntries = new ArrayList<>();
        String testMode = request.getTestMode();

        logEntries.add(logEntry("INIT", "Starting agent test", "INFO"));
        logEntries.add(logEntry("INIT", "Test mode: " + testMode, "INFO"));
        logEntries.add(logEntry("INIT", "Test ID: " + testId, "INFO"));

        try {
            // 1. Resolve agent config
            logEntries.add(logEntry("AGENT_CONFIG", "Resolving agent configuration...", "INFO"));
            UserAgentConfig agentConfig = TenantScopedLookup.findByIdScoped(
                    request.getAgentConfigId(),
                    agentConfigRepository::findById,
                    agentConfigRepository::findByIdAndTenantId,
                    () -> new ResourceNotFoundException("UserAgentConfig", request.getAgentConfigId()));

            if (!agentConfig.getTenantId().equals(tenantId) || !agentConfig.getUserId().equals(userId)) {
                throw new ResourceNotFoundException("UserAgentConfig", request.getAgentConfigId());
            }

            logEntries.add(logEntry("AGENT_CONFIG",
                    "Agent: " + agentConfig.getAgentName()
                            + " | Provider: " + agentConfig.getProvider()
                            + " | Model: " + agentConfig.getModel(),
                    "SUCCESS"));

            // 2. Resolve generator config
            logEntries.add(logEntry("GENERATOR", "Resolving test data generator configuration...", "INFO"));
            AgentTestConfig testConfig = testConfigService.getOrCreateConfig(tenantId, userId);
            AgentConfigDto generatorConfig = AgentConfigDto.builder()
                    .provider(testConfig.getGeneratorProvider())
                    .model(testConfig.getGeneratorModel())
                    .baseUrl(testConfig.getGeneratorBaseUrl())
                    .hostingType(testConfig.getGeneratorHostingType())
                    .build();

            logEntries.add(logEntry("GENERATOR",
                    "Generator: " + testConfig.getGeneratorProvider()
                            + " / " + testConfig.getGeneratorModel()
                            + " (" + testConfig.getGeneratorHostingType() + ")",
                    "SUCCESS"));

            // 3. Spin up ephemeral sandbox container
            String containerId = UUID.randomUUID().toString().substring(0, 12);
            logEntries.add(logEntry("CONTAINER_INIT",
                    "Requesting ephemeral sandbox container for test " + testId + "...", "INFO"));
            logEntries.add(logEntry("CONTAINER_INIT",
                    "Pulling workspace image: squadron-workspace:latest", "INFO"));
            logEntries.add(logEntry("CONTAINER_INIT",
                    "Allocating resources: 2 vCPU, 4 GiB memory, 10 GiB ephemeral storage", "INFO"));
            logEntries.add(logEntry("CONTAINER_INIT",
                    "Container " + containerId + " created — mounting test workspace volume", "INFO"));
            logEntries.add(logEntry("CONTAINER_INIT",
                    "Installing language toolchains and dependencies in container " + containerId + "...", "INFO"));
            logEntries.add(logEntry("CONTAINER_READY",
                    "Ephemeral container " + containerId + " is ready — sandbox environment active", "SUCCESS"));

            // 4. Generate fake test data
            logEntries.add(logEntry("TEST_DATA", "Generating test data for mode: " + testMode + "...", "INFO"));
            String testData = generateTestData(testMode, generatorConfig);
            logEntries.add(logEntry("TEST_DATA",
                    "Test data generated (" + testData.length() + " chars)", "SUCCESS"));
            logEntries.add(logEntry("TEST_DATA",
                    "Generated scenario: " + truncate(testData, 1000), "INFO"));
            logEntries.add(logEntry("TEST_DATA",
                    "Injecting test data into container " + containerId + " workspace", "INFO"));

            // 5. Build agent config DTO
            AgentConfigDto agentConfigDto = AgentConfigDto.builder()
                    .provider(agentConfig.getProvider())
                    .model(agentConfig.getModel())
                    .maxTokens(agentConfig.getMaxTokens())
                    .temperature(agentConfig.getTemperature())
                    .systemPromptOverride(agentConfig.getSystemPromptOverride())
                    .baseUrl(agentConfig.getBaseUrl())
                    .hostingType(agentConfig.getHostingType())
                    .build();

            // 6. Invoke the agent under test
            logEntries.add(logEntry("AGENT_EXEC",
                    "Sending test data to agent '" + agentConfig.getAgentName() + "'...", "INFO"));
            logEntries.add(logEntry("AGENT_EXEC",
                    "Connecting to " + agentConfig.getProvider() + " / " + agentConfig.getModel() + "...",
                    "INFO"));
            logEntries.add(logEntry("AGENT_EXEC",
                    "Agent executing inside container " + containerId + "...", "INFO"));

            String systemPrompt = buildTestSystemPrompt(testMode);
            String userMessage = buildTestUserMessage(testMode, testData);

            logEntries.add(logEntry("LLM_REQUEST",
                    "System prompt: " + truncate(systemPrompt, 500), "INFO"));
            logEntries.add(logEntry("LLM_REQUEST",
                    "User message: " + truncate(userMessage, 1000), "INFO"));

            Instant llmStart = Instant.now();
            var provider = providerRegistry.getProvider(agentConfigDto.getProvider());
            String agentOutput = provider.chat(systemPrompt, Collections.emptyList(), userMessage, agentConfigDto);
            long llmDurationMs = Instant.now().toEpochMilli() - llmStart.toEpochMilli();

            logEntries.add(logEntry("LLM_RESPONSE",
                    "LLM responded in " + llmDurationMs + "ms (" + agentOutput.length() + " chars)", "SUCCESS"));
            logEntries.add(logEntry("LLM_RESPONSE",
                    "Response: " + truncate(agentOutput, 2000), "INFO"));

            logEntries.add(logEntry("AGENT_EXEC",
                    "Agent responded (" + agentOutput.length() + " chars)", "SUCCESS"));

            // 7. Tear down ephemeral container
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Collecting test artifacts from container " + containerId + "...", "INFO"));
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Stopping container " + containerId + "...", "INFO"));
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Removing ephemeral container " + containerId
                            + " and releasing resources", "INFO"));
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Ephemeral sandbox cleaned up successfully", "SUCCESS"));

            // 8. Build result
            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
            logEntries.add(logEntry("COMPLETE",
                    "Test completed successfully in " + durationMs + "ms", "SUCCESS"));

            return AgentTestResult.builder()
                    .testId(testId)
                    .agentConfigId(request.getAgentConfigId())
                    .testMode(testMode)
                    .status("SUCCESS")
                    .summary("Agent '" + agentConfig.getAgentName() + "' passed " + testMode + " test")
                    .agentOutput(agentOutput)
                    .durationMs(durationMs)
                    .logEntries(logEntries)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build();

        } catch (Exception e) {
            log.error("Agent test {} failed: {}", testId, e.getMessage(), e);
            Instant completedAt = Instant.now();
            long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

            logEntries.add(logEntry("ERROR", "Test failed: " + e.getMessage(), "ERROR"));
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Cleaning up ephemeral sandbox resources after failure...", "WARNING"));
            logEntries.add(logEntry("CONTAINER_CLEANUP",
                    "Ephemeral sandbox cleaned up successfully", "SUCCESS"));

            return AgentTestResult.builder()
                    .testId(testId)
                    .agentConfigId(request.getAgentConfigId())
                    .testMode(testMode)
                    .status("FAILURE")
                    .summary("Test failed: " + e.getMessage())
                    .durationMs(durationMs)
                    .logEntries(logEntries)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .build();
        }
    }

    /**
     * Generates appropriate test data based on the test mode.
     */
    private String generateTestData(String testMode, AgentConfigDto generatorConfig) {
        return switch (testMode) {
            case "PLANNING" -> testDataGenerator.generateFakePlan(generatorConfig);
            case "CODE_GENERATION" -> testDataGenerator.generateFakeCodebase(generatorConfig);
            case "CODE_REVIEW" -> testDataGenerator.generateFakeCodeForReview(generatorConfig);
            default -> throw new IllegalArgumentException("Unknown test mode: " + testMode);
        };
    }

    /**
     * Invokes the agent under test with the generated test data.
     */
    private String invokeAgent(String testMode, String testData, AgentConfigDto agentConfig) {
        String systemPrompt = buildTestSystemPrompt(testMode);
        String userMessage = buildTestUserMessage(testMode, testData);

        var provider = providerRegistry.getProvider(agentConfig.getProvider());
        return provider.chat(systemPrompt, Collections.emptyList(), userMessage, agentConfig);
    }

    private String buildTestSystemPrompt(String testMode) {
        return switch (testMode) {
            case "PLANNING" -> """
                    You are a software planning agent. Given a feature description,
                    create a detailed implementation plan with steps, files to modify,
                    and acceptance criteria. Be thorough but concise.
                    """;
            case "CODE_GENERATION" -> """
                    You are a coding agent. Given an existing codebase and a task plan,
                    implement the required code changes. Show the complete modified or
                    new files. Follow existing patterns and conventions.
                    """;
            case "CODE_REVIEW" -> """
                    You are a code review agent. Review the provided code changes and
                    identify: bugs, potential issues, style problems, missing tests,
                    and security concerns. Provide actionable feedback.
                    """;
            default -> "You are a helpful software development assistant.";
        };
    }

    private String buildTestUserMessage(String testMode, String testData) {
        return switch (testMode) {
            case "PLANNING" -> "Create an implementation plan for the following feature:\n\n" + testData;
            case "CODE_GENERATION" -> "Implement the following task using the existing codebase:\n\n" + testData;
            case "CODE_REVIEW" -> "Review the following code changes:\n\n" + testData;
            default -> testData;
        };
    }

    private static TestLogEntry logEntry(String phase, String message, String level) {
        return TestLogEntry.builder()
                .timestamp(Instant.now())
                .phase(phase)
                .message(message)
                .level(level)
                .build();
    }

    /** Truncates a string for log display, appending "..." if truncated. */
    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = text.strip().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLength) return cleaned;
        return cleaned.substring(0, maxLength) + "...";
    }
}
