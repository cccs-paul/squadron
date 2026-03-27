package com.squadron.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.SquadronConfigDto;
import com.squadron.agent.entity.SquadronConfig;
import com.squadron.agent.service.SquadronConfigService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SquadronConfigController.class)
@ContextConfiguration(classes = {SquadronConfigController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class SquadronConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SquadronConfigService configService;

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
    @WithMockUser(roles = {"squadron-admin"})
    void should_createOrUpdateConfig_when_validRequest() throws Exception {
        UUID configId = UUID.randomUUID();

        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("default-config")
                .config(Map.of("provider", "openai", "model", "gpt-4"))
                .build();

        SquadronConfig savedConfig = SquadronConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .name("default-config")
                .config("{\"provider\":\"openai\",\"model\":\"gpt-4\"}")
                .build();

        when(configService.createOrUpdateConfig(any(SquadronConfigDto.class))).thenReturn(savedConfig);

        mockMvc.perform(post("/api/agents/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(configId.toString()))
                .andExpect(jsonPath("$.data.name").value("default-config"));

        verify(configService).createOrUpdateConfig(any(SquadronConfigDto.class));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_createOrUpdateConfig_withTeamAndUserScope() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("user-specific-config")
                .config(Map.of("model", "claude-3"))
                .build();

        SquadronConfig savedConfig = SquadronConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("user-specific-config")
                .config("{\"model\":\"claude-3\"}")
                .build();

        when(configService.createOrUpdateConfig(any(SquadronConfigDto.class))).thenReturn(savedConfig);

        mockMvc.perform(post("/api/agents/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_listConfigs_when_adminRole() throws Exception {
        UUID configId1 = UUID.randomUUID();
        UUID configId2 = UUID.randomUUID();

        List<SquadronConfig> configs = List.of(
                SquadronConfig.builder()
                        .id(configId1)
                        .tenantId(tenantId)
                        .name("config-1")
                        .config("{\"provider\":\"openai\"}")
                        .build(),
                SquadronConfig.builder()
                        .id(configId2)
                        .tenantId(tenantId)
                        .name("config-2")
                        .config("{\"provider\":\"anthropic\"}")
                        .build()
        );

        when(configService.listConfigsByTenant(tenantId)).thenReturn(configs);

        mockMvc.perform(get("/api/agents/config/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("config-1"))
                .andExpect(jsonPath("$.data[1].name").value("config-2"));

        verify(configService).listConfigsByTenant(tenantId);
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_resolveConfig_when_allParamsProvided() throws Exception {
        UUID teamId = UUID.randomUUID();

        SquadronConfigDto resolvedConfig = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .name("resolved")
                .config(Map.of("provider", "openai", "model", "gpt-4", "temperature", 0.7))
                .build();

        when(configService.resolveConfig(tenantId, teamId, userId)).thenReturn(resolvedConfig);

        mockMvc.perform(get("/api/agents/config/resolve")
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString())
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("resolved"))
                .andExpect(jsonPath("$.data.config.provider").value("openai"))
                .andExpect(jsonPath("$.data.config.model").value("gpt-4"));

        verify(configService).resolveConfig(tenantId, teamId, userId);
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_resolveConfig_when_onlyTenantProvided() throws Exception {
        SquadronConfigDto resolvedConfig = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("tenant-default")
                .config(Map.of("provider", "openai"))
                .build();

        when(configService.resolveConfig(eq(tenantId), any(), any())).thenReturn(resolvedConfig);

        mockMvc.perform(get("/api/agents/config/resolve")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("tenant-default"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_getConfig_when_exists() throws Exception {
        UUID configId = UUID.randomUUID();

        SquadronConfig config = SquadronConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .name("my-config")
                .config("{\"provider\":\"openai\"}")
                .build();

        when(configService.getConfig(configId)).thenReturn(config);

        mockMvc.perform(get("/api/agents/config/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(configId.toString()))
                .andExpect(jsonPath("$.data.name").value("my-config"));

        verify(configService).getConfig(configId);
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_deleteConfig_when_adminRole() throws Exception {
        UUID configId = UUID.randomUUID();

        doNothing().when(configService).deleteConfig(configId);

        mockMvc.perform(delete("/api/agents/config/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(configService).deleteConfig(configId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToCreateConfig() throws Exception {
        SquadronConfigDto dto = SquadronConfigDto.builder()
                .tenantId(tenantId)
                .name("config")
                .config(Map.of("provider", "openai"))
                .build();

        mockMvc.perform(post("/api/agents/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToListConfigs() throws Exception {
        mockMvc.perform(get("/api/agents/config/tenant/{tenantId}", tenantId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_return403_when_teamLeadTriesToDeleteConfig() throws Exception {
        mockMvc.perform(delete("/api/agents/config/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/agents/config/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_creatingConfigUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }
}
