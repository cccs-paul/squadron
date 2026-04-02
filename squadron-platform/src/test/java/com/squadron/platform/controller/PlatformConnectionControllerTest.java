package com.squadron.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.dto.CreateConnectionRequest;
import com.squadron.platform.dto.PlatformProjectDto;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.service.PlatformConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformConnectionController.class)
@ContextConfiguration(classes = {PlatformConnectionController.class, SecurityConfig.class})
class PlatformConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlatformConnectionService connectionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // --- POST /api/platforms/connections ---

    @Test
    @WithMockUser
    void should_createConnection_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .name("Jira Cloud - Production")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://myorg.atlassian.net")
                .authType("OAUTH2")
                .credentials(Map.of("clientId", "cid", "clientSecret", "sec"))
                .build();

        PlatformConnection saved = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(tenantId)
                .name("Jira Cloud - Production")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://myorg.atlassian.net")
                .authType("OAUTH2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.createConnection(any(CreateConnectionRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/platforms/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.name").value("Jira Cloud - Production"))
                .andExpect(jsonPath("$.data.platformType").value("JIRA_CLOUD"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(connectionService).createConnection(any(CreateConnectionRequest.class));
    }

    @Test
    void should_return401_when_createConnectionUnauthenticated() throws Exception {
        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(UUID.randomUUID())
                .name("Test Connection")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://example.com")
                .authType("PAT")
                .build();

        mockMvc.perform(post("/api/platforms/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/connections/tenant/{tenantId} ---

    @Test
    @WithMockUser
    void should_listByTenant_when_authenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();

        PlatformConnection conn = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("GitHub - Organization")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("OAUTH2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.listConnectionsByTenant(tenantId)).thenReturn(List.of(conn));

        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("GitHub - Organization"))
                .andExpect(jsonPath("$.data[0].platformType").value("GITHUB"));
    }

    @Test
    @WithMockUser
    void should_returnEmptyList_when_noConnectionsForTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();

        when(connectionService.listConnectionsByTenant(tenantId)).thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // --- GET /api/platforms/connections/{id} ---

    @Test
    @WithMockUser
    void should_getConnection_when_exists() throws Exception {
        UUID connectionId = UUID.randomUUID();

        PlatformConnection conn = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(UUID.randomUUID())
                .name("GitLab - Corporate")
                .platformType("GITLAB")
                .baseUrl("https://gitlab.com")
                .authType("PAT")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.getConnection(connectionId)).thenReturn(conn);

        mockMvc.perform(get("/api/platforms/connections/{id}", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.name").value("GitLab - Corporate"))
                .andExpect(jsonPath("$.data.platformType").value("GITLAB"));
    }

    @Test
    void should_return401_when_getConnectionUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/connections/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /api/platforms/connections/{id} ---

    @Test
    @WithMockUser
    void should_updateConnection_when_validRequest() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CreateConnectionRequest request = CreateConnectionRequest.builder()
                .tenantId(tenantId)
                .name("Updated Jira Cloud")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://updated.atlassian.net")
                .authType("OAUTH2")
                .build();

        PlatformConnection updated = PlatformConnection.builder()
                .id(connectionId)
                .tenantId(tenantId)
                .name("Updated Jira Cloud")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://updated.atlassian.net")
                .authType("OAUTH2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.updateConnection(eq(connectionId), any(CreateConnectionRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/platforms/connections/{id}", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Jira Cloud"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://updated.atlassian.net"));

        verify(connectionService).updateConnection(eq(connectionId), any(CreateConnectionRequest.class));
    }

    // --- DELETE /api/platforms/connections/{id} ---

    @Test
    @WithMockUser
    void should_deleteConnection_when_authenticated() throws Exception {
        UUID connectionId = UUID.randomUUID();

        doNothing().when(connectionService).deleteConnection(connectionId);

        mockMvc.perform(delete("/api/platforms/connections/{id}", connectionId))
                .andExpect(status().isNoContent());

        verify(connectionService).deleteConnection(connectionId);
    }

    @Test
    void should_return401_when_deleteConnectionUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/platforms/connections/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/platforms/connections/{id}/test ---

    @Test
    @WithMockUser
    void should_testConnection_when_success() throws Exception {
        UUID connectionId = UUID.randomUUID();

        when(connectionService.testConnection(connectionId)).thenReturn(true);

        mockMvc.perform(post("/api/platforms/connections/{id}/test", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @WithMockUser
    void should_testConnection_when_failure() throws Exception {
        UUID connectionId = UUID.randomUUID();

        when(connectionService.testConnection(connectionId)).thenReturn(false);

        mockMvc.perform(post("/api/platforms/connections/{id}/test", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));
    }

    // --- GET /api/platforms/connections/{id}/statuses ---

    @Test
    @WithMockUser
    void should_getProjectStatuses_when_validRequest() throws Exception {
        UUID connectionId = UUID.randomUUID();
        List<String> statuses = List.of("To Do", "In Progress", "In Review", "Done");

        when(connectionService.fetchProjectStatuses(connectionId, "PROJ-1")).thenReturn(statuses);

        mockMvc.perform(get("/api/platforms/connections/{id}/statuses", connectionId)
                        .param("projectKey", "PROJ-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("To Do"))
                .andExpect(jsonPath("$.data[1]").value("In Progress"))
                .andExpect(jsonPath("$.data[2]").value("In Review"))
                .andExpect(jsonPath("$.data[3]").value("Done"));

        verify(connectionService).fetchProjectStatuses(connectionId, "PROJ-1");
    }

    @Test
    @WithMockUser
    void should_returnEmptyStatuses_when_noStatusesFound() throws Exception {
        UUID connectionId = UUID.randomUUID();

        when(connectionService.fetchProjectStatuses(connectionId, "EMPTY")).thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/connections/{id}/statuses", connectionId)
                        .param("projectKey", "EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void should_return401_when_getStatusesUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/connections/{id}/statuses", UUID.randomUUID())
                        .param("projectKey", "PROJ-1"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/connections/{id}/projects ---

    @Test
    @WithMockUser
    void should_getRemoteProjects_when_validRequest() throws Exception {
        UUID connectionId = UUID.randomUUID();
        List<PlatformProjectDto> projects = List.of(
                PlatformProjectDto.builder()
                        .key("PROJ")
                        .name("My Project")
                        .description("A test project")
                        .url("https://example.atlassian.net/browse/PROJ")
                        .avatarUrl("https://example.atlassian.net/avatar.png")
                        .build(),
                PlatformProjectDto.builder()
                        .key("DEMO")
                        .name("Demo Project")
                        .description(null)
                        .url("https://example.atlassian.net/browse/DEMO")
                        .avatarUrl(null)
                        .build()
        );

        when(connectionService.fetchProjects(connectionId)).thenReturn(projects);

        mockMvc.perform(get("/api/platforms/connections/{id}/projects", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].key").value("PROJ"))
                .andExpect(jsonPath("$.data[0].name").value("My Project"))
                .andExpect(jsonPath("$.data[0].description").value("A test project"))
                .andExpect(jsonPath("$.data[0].url").value("https://example.atlassian.net/browse/PROJ"))
                .andExpect(jsonPath("$.data[0].avatarUrl").value("https://example.atlassian.net/avatar.png"))
                .andExpect(jsonPath("$.data[1].key").value("DEMO"))
                .andExpect(jsonPath("$.data[1].name").value("Demo Project"))
                .andExpect(jsonPath("$.data[1].description").doesNotExist());

        verify(connectionService).fetchProjects(connectionId);
    }

    @Test
    @WithMockUser
    void should_returnEmptyProjects_when_noRemoteProjects() throws Exception {
        UUID connectionId = UUID.randomUUID();

        when(connectionService.fetchProjects(connectionId)).thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/connections/{id}/projects", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void should_return401_when_getProjectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/connections/{id}/projects", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/connections/tenant/{tenantId}/category/{category} ---

    @Test
    @WithMockUser
    void should_listByTenantAndCategory_when_gitRemote() throws Exception {
        UUID tenantId = UUID.randomUUID();

        PlatformConnection conn = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("GitHub - Org")
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("PAT")
                .status("ACTIVE")
                .platformCategory("GIT_REMOTE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.listConnectionsByTenantAndCategory(tenantId, "GIT_REMOTE"))
                .thenReturn(List.of(conn));

        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}/category/{category}", tenantId, "GIT_REMOTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("GitHub - Org"))
                .andExpect(jsonPath("$.data[0].platformType").value("GITHUB"));
    }

    @Test
    @WithMockUser
    void should_returnEmptyList_when_noCategoryConnections() throws Exception {
        UUID tenantId = UUID.randomUUID();

        when(connectionService.listConnectionsByTenantAndCategory(tenantId, "TICKET_PROVIDER"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}/category/{category}", tenantId, "TICKET_PROVIDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void should_return401_when_listByCategoryUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}/category/{category}",
                UUID.randomUUID(), "GIT_REMOTE"))
                .andExpect(status().isUnauthorized());
    }

    // --- Response should not expose credentials ---

    @Test
    @WithMockUser
    void should_notExposeCredentials_when_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();

        PlatformConnection conn = PlatformConnection.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Jira Prod")
                .platformType("JIRA_CLOUD")
                .baseUrl("https://myorg.atlassian.net")
                .authType("API_TOKEN")
                .credentials("{\"apiToken\":\"secret-value\"}")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(connectionService.listConnectionsByTenant(tenantId)).thenReturn(List.of(conn));

        mockMvc.perform(get("/api/platforms/connections/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Jira Prod"))
                .andExpect(jsonPath("$.data[0].credentials").doesNotExist())
                .andExpect(jsonPath("$.data[0].metadata").doesNotExist());
    }
}
