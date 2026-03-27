package com.squadron.config.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.config.dto.ConfigUpdateRequest;
import com.squadron.config.dto.ResolvedConfigDto;
import com.squadron.config.entity.ConfigAuditLog;
import com.squadron.config.entity.ConfigEntry;
import com.squadron.config.service.ConfigService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigController.class)
@ContextConfiguration(classes = {ConfigController.class, com.squadron.config.config.SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConfigService configService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // ============================================================
    // resolveConfig tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_resolveConfig_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String key = "max.retries";

        ResolvedConfigDto resolved = ResolvedConfigDto.builder()
                .configKey(key)
                .resolvedValue("5")
                .resolvedFrom("USER")
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .build();

        when(configService.resolveConfig(tenantId, teamId, userId, key)).thenReturn(resolved);

        mockMvc.perform(get("/api/config/resolve")
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString())
                        .param("userId", userId.toString())
                        .param("key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configKey").value(key))
                .andExpect(jsonPath("$.data.resolvedValue").value("5"))
                .andExpect(jsonPath("$.data.resolvedFrom").value("USER"));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_resolveConfig_when_viewerRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String key = "timeout";

        ResolvedConfigDto resolved = ResolvedConfigDto.builder()
                .configKey(key)
                .resolvedValue("30")
                .resolvedFrom("TENANT")
                .tenantId(tenantId)
                .build();

        when(configService.resolveConfig(eq(tenantId), isNull(), isNull(), eq(key))).thenReturn(resolved);

        mockMvc.perform(get("/api/config/resolve")
                        .param("tenantId", tenantId.toString())
                        .param("key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolvedValue").value("30"));
    }

    // ============================================================
    // listTenantConfigs tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_listTenantConfigs_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntry entry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configKey("feature.flag")
                .configValue("true")
                .description("A feature flag")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(configService.listConfigs(eq(tenantId), isNull(), isNull())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/config/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("feature.flag"))
                .andExpect(jsonPath("$.data[0].configValue").value("true"));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_returnEmptyList_when_noTenantConfigs() throws Exception {
        UUID tenantId = UUID.randomUUID();

        when(configService.listConfigs(eq(tenantId), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/config/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================================================
    // listTeamConfigs tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_listTeamConfigs_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntry entry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .configKey("team.setting")
                .configValue("value")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(configService.listConfigs(eq(tenantId), eq(teamId), isNull())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/config/tenant/{tenantId}/team/{teamId}", tenantId, teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("team.setting"));
    }

    // ============================================================
    // listUserConfigs tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"developer"})
    void should_listUserConfigs_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigEntry entry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .configKey("user.pref")
                .configValue("dark")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(configService.listConfigs(eq(tenantId), isNull(), eq(userId))).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/config/tenant/{tenantId}/user/{userId}", tenantId, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("user.pref"));
    }

    // ============================================================
    // setConfig tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_setConfig_when_adminRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("new.key")
                .configValue("new.value")
                .description("New config")
                .build();

        ConfigEntry savedEntry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configKey("new.key")
                .configValue("new.value")
                .description("New config")
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(configService.setConfig(eq(tenantId), isNull(), isNull(), any(ConfigUpdateRequest.class)))
                .thenReturn(savedEntry);

        mockMvc.perform(post("/api/config")
                        .with(csrf())
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configKey").value("new.key"))
                .andExpect(jsonPath("$.data.configValue").value("new.value"));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_setConfig_when_teamLeadRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("team.config")
                .configValue("team.value")
                .build();

        ConfigEntry savedEntry = ConfigEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .configKey("team.config")
                .configValue("team.value")
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(configService.setConfig(eq(tenantId), eq(teamId), isNull(), any(ConfigUpdateRequest.class)))
                .thenReturn(savedEntry);

        mockMvc.perform(post("/api/config")
                        .with(csrf())
                        .param("tenantId", tenantId.toString())
                        .param("teamId", teamId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.configKey").value("team.config"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToSetConfig() throws Exception {
        UUID tenantId = UUID.randomUUID();

        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key")
                .configValue("value")
                .build();

        mockMvc.perform(post("/api/config")
                        .with(csrf())
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // deleteConfig tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_deleteConfig_when_adminRole() throws Exception {
        UUID configId = UUID.randomUUID();

        doNothing().when(configService).deleteConfig(configId);

        mockMvc.perform(delete("/api/config/{id}", configId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(configService).deleteConfig(configId);
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToDeleteConfig() throws Exception {
        UUID configId = UUID.randomUUID();

        mockMvc.perform(delete("/api/config/{id}", configId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // getAuditLog tests
    // ============================================================

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_getAuditLog_when_validRequest() throws Exception {
        UUID configEntryId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        ConfigAuditLog log1 = ConfigAuditLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .configEntryId(configEntryId)
                .configKey("some.key")
                .previousValue("old")
                .newValue("new")
                .changedAt(now)
                .build();

        when(configService.getAuditLog(configEntryId)).thenReturn(List.of(log1));

        mockMvc.perform(get("/api/config/audit/{configEntryId}", configEntryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("some.key"))
                .andExpect(jsonPath("$.data[0].previousValue").value("old"))
                .andExpect(jsonPath("$.data[0].newValue").value("new"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_returnEmptyAuditLog_when_noEntriesExist() throws Exception {
        UUID configEntryId = UUID.randomUUID();

        when(configService.getAuditLog(configEntryId)).thenReturn(List.of());

        mockMvc.perform(get("/api/config/audit/{configEntryId}", configEntryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================================================
    // Unauthenticated tests
    // ============================================================

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/config/resolve")
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("key", "some.key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_unauthenticatedPost() throws Exception {
        mockMvc.perform(post("/api/config")
                        .with(csrf())
                        .param("tenantId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configKey\":\"k\",\"configValue\":\"v\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_unauthenticatedDelete() throws Exception {
        mockMvc.perform(delete("/api/config/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
