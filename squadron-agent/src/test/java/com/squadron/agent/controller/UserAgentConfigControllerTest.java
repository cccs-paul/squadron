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
                buildAgent("Architect", "PLANNING", 0),
                buildAgent("Maverick", "CODING", 1)
        );

        when(service.getUserSquadron(tenantId, userId)).thenReturn(agents);

        mockMvc.perform(get("/api/agents/squadron"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentName").value("Architect"))
                .andExpect(jsonPath("$.data[0].agentType").value("PLANNING"))
                .andExpect(jsonPath("$.data[1].agentName").value("Maverick"));

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
                .agentType("CODING")
                .displayOrder(5)
                .provider("openai-compatible")
                .model("gpt-4o")
                .enabled(true)
                .build();

        UserAgentConfig savedAgent = buildAgent("New Agent", "CODING", 5);
        savedAgent.setProvider("openai-compatible");
        savedAgent.setModel("gpt-4o");

        when(service.addAgent(eq(tenantId), eq(userId), any(UserAgentConfigDto.class)))
                .thenReturn(savedAgent);

        mockMvc.perform(post("/api/agents/squadron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("New Agent"))
                .andExpect(jsonPath("$.data.agentType").value("CODING"))
                .andExpect(jsonPath("$.data.provider").value("openai-compatible"));

        verify(service).addAgent(eq(tenantId), eq(userId), any(UserAgentConfigDto.class));
    }

    @Test
    void should_return401_when_addingAgentUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/squadron")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentName\":\"Test\",\"agentType\":\"CODING\"}"))
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
                .agentType("REVIEW")
                .displayOrder(2)
                .enabled(true)
                .build();

        UserAgentConfig updatedAgent = buildAgent("Updated Name", "REVIEW", 2);

        when(service.updateAgent(eq(tenantId), eq(userId), eq(agentId), any(UserAgentConfigDto.class)))
                .thenReturn(updatedAgent);

        mockMvc.perform(put("/api/agents/squadron/{agentId}", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentName").value("Updated Name"))
                .andExpect(jsonPath("$.data.agentType").value("REVIEW"));

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
                buildAgent("Architect", "PLANNING", 0),
                buildAgent("Maverick", "CODING", 1)
        );

        when(service.resetToDefaults(tenantId, userId)).thenReturn(defaults);

        mockMvc.perform(post("/api/agents/squadron/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentName").value("Architect"));

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

    private UserAgentConfig buildAgent(String name, String type, int order) {
        return UserAgentConfig.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .agentName(name)
                .agentType(type)
                .displayOrder(order)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
