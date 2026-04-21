package com.squadron.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.AgentTestConfigDto;
import com.squadron.agent.dto.AgentTestRequest;
import com.squadron.agent.dto.AgentTestResult;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.dto.InteractiveTestSessionDto.InteractiveTestMessage;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.service.AgentTestConfigService;
import com.squadron.agent.service.AgentTestExecutionService;
import com.squadron.agent.service.InteractiveTestSessionService;
import com.squadron.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentTestController.class)
@ContextConfiguration(classes = {AgentTestController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class AgentTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentTestExecutionService executionService;

    @MockBean
    private AgentTestConfigService configService;

    @MockBean
    private InteractiveTestSessionService interactiveService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_executeTest_when_validRequest() throws Exception {
        UUID agentConfigId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();

        AgentTestResult result = AgentTestResult.builder()
                .testId(testId)
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .status("SUCCESS")
                .summary("Agent 'Sol' passed PLANNING test")
                .agentOutput("Generated plan content")
                .durationMs(1500L)
                .logEntries(List.of())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        when(executionService.executeTest(eq(tenantId), eq(userId), any(AgentTestRequest.class)))
                .thenReturn(result);

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        mockMvc.perform(post("/api/agents/test/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.testMode").value("PLANNING"))
                .andExpect(jsonPath("$.data.summary").value("Agent 'Sol' passed PLANNING test"));

        verify(executionService).executeTest(eq(tenantId), eq(userId), any(AgentTestRequest.class));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnTestConfig_when_authenticated() throws Exception {
        AgentTestConfig config = AgentTestConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .generatorProvider("ollama")
                .generatorModel("gemma4:e2b")
                .generatorHostingType("SELF_HOSTED")
                .build();

        AgentTestConfigDto dto = AgentTestConfigDto.builder()
                .generatorProvider("ollama")
                .generatorModel("gemma4:e2b")
                .generatorHostingType("SELF_HOSTED")
                .build();

        when(configService.getOrCreateConfig(tenantId, userId)).thenReturn(config);
        when(configService.toDto(config)).thenReturn(dto);

        mockMvc.perform(get("/api/agents/test/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generatorProvider").value("ollama"))
                .andExpect(jsonPath("$.data.generatorModel").value("gemma4:e2b"))
                .andExpect(jsonPath("$.data.generatorHostingType").value("SELF_HOSTED"));

        verify(configService).getOrCreateConfig(tenantId, userId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_updateTestConfig_when_validDto() throws Exception {
        AgentTestConfig savedConfig = AgentTestConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .generatorHostingType("PLATFORM")
                .build();

        AgentTestConfigDto responseDto = AgentTestConfigDto.builder()
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .generatorHostingType("PLATFORM")
                .generatorApiKey("********")
                .build();

        when(configService.updateConfig(eq(tenantId), eq(userId), any(AgentTestConfigDto.class)))
                .thenReturn(savedConfig);
        when(configService.toDto(savedConfig)).thenReturn(responseDto);

        AgentTestConfigDto requestDto = AgentTestConfigDto.builder()
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .generatorHostingType("PLATFORM")
                .generatorApiKey("sk-secret")
                .build();

        mockMvc.perform(put("/api/agents/test/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generatorProvider").value("openai"))
                .andExpect(jsonPath("$.data.generatorModel").value("gpt-4o"))
                .andExpect(jsonPath("$.data.generatorApiKey").value("********"));

        verify(configService).updateConfig(eq(tenantId), eq(userId), any(AgentTestConfigDto.class));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return400_when_missingAgentConfigId() throws Exception {
        String json = "{\"testMode\": \"PLANNING\"}";

        mockMvc.perform(post("/api/agents/test/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return400_when_missingTestMode() throws Exception {
        String json = "{\"agentConfigId\": \"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/agents/test/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_streamTestExecution_when_validRequest() throws Exception {
        UUID agentConfigId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();

        AgentTestResult result = AgentTestResult.builder()
                .testId(testId)
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .status("SUCCESS")
                .summary("Agent 'Sol' passed PLANNING test")
                .agentOutput("Generated plan content")
                .durationMs(1500L)
                .logEntries(List.of())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        ServerSentEvent<AgentTestResult> sse = ServerSentEvent.<AgentTestResult>builder()
                .event("complete")
                .data(result)
                .build();

        when(executionService.executeTestStreaming(eq(tenantId), eq(userId), any(AgentTestRequest.class)))
                .thenReturn(Flux.just(sse));

        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(agentConfigId)
                .testMode("PLANNING")
                .build();

        mockMvc.perform(post("/api/agents/test/execute/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(executionService).executeTestStreaming(eq(tenantId), eq(userId), any(AgentTestRequest.class));
    }

    // ========================= Interactive Test Endpoints =========================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_startInteractiveSession_when_validRequest() throws Exception {
        UUID agentConfigId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        InteractiveTestSessionDto session = InteractiveTestSessionDto.builder()
                .sessionId(sessionId)
                .agentConfigId(agentConfigId)
                .agentName("Sol")
                .provider("ollama")
                .model("gemma4")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .messages(List.of(
                        InteractiveTestMessage.builder()
                                .id(UUID.randomUUID())
                                .role("SYSTEM")
                                .content("Session started with Sol")
                                .createdAt(Instant.now())
                                .build()
                ))
                .build();

        when(interactiveService.startSession(eq(tenantId), eq(userId), eq(agentConfigId)))
                .thenReturn(session);

        mockMvc.perform(post("/api/agents/test/interactive/start")
                        .param("agentConfigId", agentConfigId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("Sol"))
                .andExpect(jsonPath("$.data.provider").value("ollama"))
                .andExpect(jsonPath("$.data.model").value("gemma4"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(interactiveService).startSession(eq(tenantId), eq(userId), eq(agentConfigId));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getInteractiveSession_when_sessionExists() throws Exception {
        UUID sessionId = UUID.randomUUID();

        InteractiveTestSessionDto session = InteractiveTestSessionDto.builder()
                .sessionId(sessionId)
                .agentConfigId(UUID.randomUUID())
                .agentName("Titan")
                .provider("ollama")
                .model("qwen2.5-coder")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .messages(List.of())
                .build();

        when(interactiveService.getSession(eq(sessionId), eq(tenantId), eq(userId)))
                .thenReturn(session);

        mockMvc.perform(get("/api/agents/test/interactive/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("Titan"));

        verify(interactiveService).getSession(eq(sessionId), eq(tenantId), eq(userId));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_listInteractiveSessions_when_userHasSessions() throws Exception {
        InteractiveTestSessionDto session = InteractiveTestSessionDto.builder()
                .sessionId(UUID.randomUUID())
                .agentConfigId(UUID.randomUUID())
                .agentName("Sol")
                .provider("ollama")
                .model("gemma4")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .messages(List.of())
                .build();

        when(interactiveService.getUserSessions(eq(tenantId), eq(userId)))
                .thenReturn(List.of(session));

        mockMvc.perform(get("/api/agents/test/interactive/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].agentName").value("Sol"));

        verify(interactiveService).getUserSessions(eq(tenantId), eq(userId));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_closeInteractiveSession_when_sessionExists() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/agents/test/interactive/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(interactiveService).closeSession(eq(sessionId), eq(tenantId), eq(userId));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_streamInteractiveMessage_when_validRequest() throws Exception {
        UUID sessionId = UUID.randomUUID();

        InteractiveTestSessionDto snapshot = InteractiveTestSessionDto.builder()
                .sessionId(sessionId)
                .agentConfigId(UUID.randomUUID())
                .agentName("Sol")
                .provider("ollama")
                .model("gemma4")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .messages(List.of())
                .build();

        ServerSentEvent<InteractiveTestSessionDto> sse = ServerSentEvent.<InteractiveTestSessionDto>builder()
                .event("snapshot")
                .data(snapshot)
                .build();

        when(interactiveService.sendMessage(eq(tenantId), eq(userId), any()))
                .thenReturn(Flux.just(sse));

        String json = "{\"sessionId\":\"" + sessionId + "\",\"message\":\"Hello\"}";

        mockMvc.perform(post("/api/agents/test/interactive/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content(json))
                .andExpect(status().isOk());

        verify(interactiveService).sendMessage(eq(tenantId), eq(userId), any());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return400_when_interactiveMessageMissingSessionId() throws Exception {
        String json = "{\"message\":\"Hello\"}";

        mockMvc.perform(post("/api/agents/test/interactive/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return400_when_interactiveMessageMissingMessage() throws Exception {
        String json = "{\"sessionId\":\"" + UUID.randomUUID() + "\"}";

        mockMvc.perform(post("/api/agents/test/interactive/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return400_when_interactiveMessageBlankMessage() throws Exception {
        String json = "{\"sessionId\":\"" + UUID.randomUUID() + "\",\"message\":\"  \"}";

        mockMvc.perform(post("/api/agents/test/interactive/message/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
