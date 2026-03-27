package com.squadron.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.exception.GlobalExceptionHandler;
import com.squadron.identity.exception.ResourceNotFoundException;
import com.squadron.identity.service.AuthProviderConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthProviderConfigControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthProviderConfigService authProviderConfigService;

    @InjectMocks
    private AuthProviderConfigController authProviderConfigController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authProviderConfigController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_return201_when_createConfigSuccessful() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        AuthProviderConfig created = AuthProviderConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .providerType("ldap")
                .name("Corporate LDAP")
                .config("{\"url\":\"ldap://ldap.example.com\"}")
                .enabled(true)
                .priority(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(authProviderConfigService.createConfig(tenantId, "ldap", "Corporate LDAP",
                "{\"url\":\"ldap://ldap.example.com\"}", true, 1))
                .thenReturn(created);

        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId.toString());
        body.put("providerType", "ldap");
        body.put("name", "Corporate LDAP");
        body.put("config", "{\"url\":\"ldap://ldap.example.com\"}");
        body.put("enabled", "true");
        body.put("priority", "1");

        mockMvc.perform(post("/api/auth-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.providerType").value("ldap"))
                .andExpect(jsonPath("$.data.name").value("Corporate LDAP"));
    }

    @Test
    void should_return201_when_createConfigWithDefaults() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        AuthProviderConfig created = AuthProviderConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .providerType("oidc")
                .name("SSO")
                .config("{}")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(authProviderConfigService.createConfig(tenantId, "oidc", "SSO", "{}", true, 0))
                .thenReturn(created);

        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId.toString());
        body.put("providerType", "oidc");
        body.put("name", "SSO");

        mockMvc.perform(post("/api/auth-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("SSO"));
    }

    @Test
    void should_returnConfigs_when_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AuthProviderConfig c1 = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .name("LDAP")
                .providerType("ldap")
                .build();
        AuthProviderConfig c2 = AuthProviderConfig.builder()
                .id(UUID.randomUUID())
                .name("OIDC")
                .providerType("oidc")
                .build();
        when(authProviderConfigService.listConfigs(tenantId)).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/auth-providers")
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void should_returnConfig_when_getById() throws Exception {
        UUID configId = UUID.randomUUID();
        AuthProviderConfig config = AuthProviderConfig.builder()
                .id(configId)
                .name("Corporate LDAP")
                .providerType("ldap")
                .config("{\"url\":\"ldap://ldap.example.com\"}")
                .build();
        when(authProviderConfigService.getConfig(configId)).thenReturn(config);

        mockMvc.perform(get("/api/auth-providers/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Corporate LDAP"));
    }

    @Test
    void should_return404_when_configNotFound() throws Exception {
        UUID configId = UUID.randomUUID();
        when(authProviderConfigService.getConfig(configId))
                .thenThrow(new ResourceNotFoundException("AuthProviderConfig", "id", configId));

        mockMvc.perform(get("/api/auth-providers/{id}", configId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_returnUpdatedConfig_when_updateSuccessful() throws Exception {
        UUID configId = UUID.randomUUID();
        AuthProviderConfig updated = AuthProviderConfig.builder()
                .id(configId)
                .name("Updated LDAP")
                .providerType("ldap")
                .config("{\"url\":\"ldap://new.example.com\"}")
                .enabled(false)
                .priority(5)
                .build();
        when(authProviderConfigService.updateConfig(configId, "Updated LDAP", "ldap",
                "{\"url\":\"ldap://new.example.com\"}", false, 5))
                .thenReturn(updated);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Updated LDAP");
        body.put("providerType", "ldap");
        body.put("config", "{\"url\":\"ldap://new.example.com\"}");
        body.put("enabled", "false");
        body.put("priority", "5");

        mockMvc.perform(put("/api/auth-providers/{id}", configId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated LDAP"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void should_return200_when_deleteConfigSuccessful() throws Exception {
        UUID configId = UUID.randomUUID();
        doNothing().when(authProviderConfigService).deleteConfig(configId);

        mockMvc.perform(delete("/api/auth-providers/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authProviderConfigService).deleteConfig(configId);
    }

    @Test
    void should_returnPartiallyUpdatedConfig_when_updateWithPartialBody() throws Exception {
        UUID configId = UUID.randomUUID();
        AuthProviderConfig updated = AuthProviderConfig.builder()
                .id(configId)
                .name("New Name")
                .providerType("ldap")
                .build();
        when(authProviderConfigService.updateConfig(configId, "New Name", null, null, null, null))
                .thenReturn(updated);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Name");

        mockMvc.perform(put("/api/auth-providers/{id}", configId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("New Name"));
    }
}
