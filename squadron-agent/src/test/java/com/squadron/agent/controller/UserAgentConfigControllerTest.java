package com.squadron.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.UserAgentConfigDto;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.service.UserAgentConfigService;
import com.squadron.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAgentConfigController.class)
@ContextConfiguration(classes = {UserAgentConfigController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class UserAgentConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAgentConfigService service;

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

    // ============================================================
    // GET /api/agents/squadron
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getMySquadron_when_authenticated() throws Exception {
        List<UserAgentConfig> agents = List.of(
                buildAgent("Sol", "GENERAL", 0, "github-copilot", "claude-sonnet-4",
                        "PLATFORM", "Claude Sonnet 4 via GitHub Copilot"),
                buildAgent("Titan", "GENERAL", 1, "github-copilot", "gpt-4o",
                        "PLATFORM", "GPT-4o via GitHub Copilot")
        );

        when(service.getUserSquadron(tenantId, userId)).thenReturn(agents);

        mockMvc.perform(get("/api/agents/squadron"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentName").value("Sol"))
                .andExpect(jsonPath("$.data[0].agentType").value("GENERAL"))
                .andExpect(jsonPath("$.data[0].provider").value("github-copilot"))
                .andExpect(jsonPath("$.data[0].model").value("claude-sonnet-4"))
                .andExpect(jsonPath("$.data[0].hostingType").value("PLATFORM"))
                .andExpect(jsonPath("$.data[0].description").value("Claude Sonnet 4 via GitHub Copilot"))
                .andExpect(jsonPath("$.data[1].agentName").value("Titan"))
                .andExpect(jsonPath("$.data[1].description").value("GPT-4o via GitHub Copilot"));

        verify(service).getUserSquadron(tenantId, userId);
    }

    @Test
    void should_return401_when_getSquadronUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/agents/squadron"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/agents/squadron/limits
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getLimits_when_authenticated() throws Exception {
        when(service.getMaxAgentsPerUser()).thenReturn(8);

        mockMvc.perform(get("/api/agents/squadron/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maxAgentsPerUser").value(8));
    }

    // ============================================================
    // POST /api/agents/squadron
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_addAgent_when_validRequest() throws Exception {
        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("New Agent")
                .agentType("GENERAL")
                .displayOrder(5)
                .provider("openai")
                .model("gpt-4o")
                .hostingType("PLATFORM")
                .description("GPT-4o via OpenAI")
                .enabled(true)
                .build();

        UserAgentConfig savedAgent = buildAgent("New Agent", "GENERAL", 5,
                "openai", "gpt-4o", "PLATFORM", "GPT-4o via OpenAI");

        when(service.addAgent(eq(tenantId), eq(userId), any(UserAgentConfigDto.class)))
                .thenReturn(savedAgent);

        mockMvc.perform(post("/api/agents/squadron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("New Agent"))
                .andExpect(jsonPath("$.data.agentType").value("GENERAL"))
                .andExpect(jsonPath("$.data.provider").value("openai"))
                .andExpect(jsonPath("$.data.model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.hostingType").value("PLATFORM"))
                .andExpect(jsonPath("$.data.description").value("GPT-4o via OpenAI"));

        verify(service).addAgent(eq(tenantId), eq(userId), any(UserAgentConfigDto.class));
    }

    @Test
    void should_return401_when_addingAgentUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/squadron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentName\":\"Test\",\"agentType\":\"GENERAL\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // PUT /api/agents/squadron/{agentId}
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_updateAgent_when_validRequest() throws Exception {
        UUID agentId = UUID.randomUUID();

        UserAgentConfigDto dto = UserAgentConfigDto.builder()
                .agentName("Updated Name")
                .agentType("GENERAL")
                .displayOrder(2)
                .provider("anthropic")
                .model("claude-opus-4")
                .hostingType("PLATFORM")
                .description("Claude Opus 4 via Anthropic")
                .enabled(true)
                .build();

        UserAgentConfig updatedAgent = buildAgent("Updated Name", "GENERAL", 2,
                "anthropic", "claude-opus-4", "PLATFORM", "Claude Opus 4 via Anthropic");

        when(service.updateAgent(eq(tenantId), eq(userId), eq(agentId), any(UserAgentConfigDto.class)))
                .thenReturn(updatedAgent);

        mockMvc.perform(put("/api/agents/squadron/{agentId}", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("Updated Name"))
                .andExpect(jsonPath("$.data.agentType").value("GENERAL"))
                .andExpect(jsonPath("$.data.provider").value("anthropic"))
                .andExpect(jsonPath("$.data.hostingType").value("PLATFORM"))
                .andExpect(jsonPath("$.data.description").value("Claude Opus 4 via Anthropic"));

        verify(service).updateAgent(eq(tenantId), eq(userId), eq(agentId), any(UserAgentConfigDto.class));
    }

    // ============================================================
    // DELETE /api/agents/squadron/{agentId}
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_removeAgent_when_authenticated() throws Exception {
        UUID agentId = UUID.randomUUID();

        doNothing().when(service).removeAgent(tenantId, userId, agentId);

        mockMvc.perform(delete("/api/agents/squadron/{agentId}", agentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).removeAgent(tenantId, userId, agentId);
    }

    @Test
    void should_return401_when_removingAgentUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/agents/squadron/{agentId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // POST /api/agents/squadron/reset
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_resetToDefaults_when_authenticated() throws Exception {
        List<UserAgentConfig> defaults = List.of(
                buildAgent("Sol", "GENERAL", 0, "github-copilot", "claude-sonnet-4",
                        "PLATFORM", "Claude Sonnet 4 via GitHub Copilot"),
                buildAgent("Titan", "GENERAL", 1, "github-copilot", "gpt-4o",
                        "PLATFORM", "GPT-4o via GitHub Copilot")
        );

        when(service.resetToDefaults(tenantId, userId)).thenReturn(defaults);

        mockMvc.perform(post("/api/agents/squadron/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentName").value("Sol"))
                .andExpect(jsonPath("$.data[0].description").value("Claude Sonnet 4 via GitHub Copilot"));

        verify(service).resetToDefaults(tenantId, userId);
    }

    @Test
    void should_return401_when_resetUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/squadron/reset"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private UserAgentConfig buildAgent(String name, String type, int order,
                                       String provider, String model,
                                       String hostingType, String description) {
        return UserAgentConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .agentName(name)
                .agentType(type)
                .displayOrder(order)
                .provider(provider)
                .model(model)
                .hostingType(hostingType)
                .description(description)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
