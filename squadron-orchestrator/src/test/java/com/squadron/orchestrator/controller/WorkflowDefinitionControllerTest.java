package com.squadron.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.config.SecurityConfig;
import com.squadron.orchestrator.dto.CreateWorkflowDefinitionRequest;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.service.WorkflowDefinitionService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkflowDefinitionController.class)
@ContextConfiguration(classes = {WorkflowDefinitionController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class WorkflowDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowDefinitionService workflowDefinitionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_create_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Default Workflow")
                .states("[\"BACKLOG\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"IN_PROGRESS\"}]")
                .hooks("{}")
                .build();

        WorkflowDefinition saved = WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Default Workflow")
                .states("[\"BACKLOG\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"IN_PROGRESS\"}]")
                .hooks("{}")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(workflowDefinitionService.create(any(CreateWorkflowDefinitionRequest.class)))
                .thenReturn(saved);

        mockMvc.perform(post("/api/workflow-definitions")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("squadron-admin")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_squadron-admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Default Workflow"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void should_create_when_teamLead() throws Exception {
        UUID tenantId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .name("Team Workflow")
                .states("[\"TODO\",\"DONE\"]")
                .transitions("[{\"from\":\"TODO\",\"to\":\"DONE\"}]")
                .hooks("{}")
                .build();

        WorkflowDefinition saved = WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name("Team Workflow")
                .states("[\"TODO\",\"DONE\"]")
                .transitions("[{\"from\":\"TODO\",\"to\":\"DONE\"}]")
                .hooks("{}")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(workflowDefinitionService.create(any(CreateWorkflowDefinitionRequest.class)))
                .thenReturn(saved);

        mockMvc.perform(post("/api/workflow-definitions")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("team-lead")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_team-lead")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Team Workflow"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getById_when_exists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        WorkflowDefinition definition = WorkflowDefinition.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Found Workflow")
                .states("[\"BACKLOG\"]")
                .transitions("[]")
                .hooks("{}")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(workflowDefinitionService.getById(id)).thenReturn(definition);

        mockMvc.perform(get("/api/workflow-definitions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Found Workflow"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();

        List<WorkflowDefinition> definitions = List.of(
                WorkflowDefinition.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .name("WF1")
                        .states("[]")
                        .transitions("[]")
                        .hooks("{}")
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                WorkflowDefinition.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .name("WF2")
                        .states("[]")
                        .transitions("[]")
                        .hooks("{}")
                        .active(false)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );

        when(workflowDefinitionService.listByTenant(tenantId)).thenReturn(definitions);

        mockMvc.perform(get("/api/workflow-definitions/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("WF1"))
                .andExpect(jsonPath("$.data[1].name").value("WF2"))
                .andExpect(jsonPath("$.data[1].active").value(false));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_update_when_validRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .name("Updated Workflow")
                .states("[\"NEW_STATE\"]")
                .transitions("[{\"from\":\"NEW\",\"to\":\"NEW\"}]")
                .hooks("{\"updated\":true}")
                .build();

        WorkflowDefinition updated = WorkflowDefinition.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Updated Workflow")
                .states("[\"NEW_STATE\"]")
                .transitions("[{\"from\":\"NEW\",\"to\":\"NEW\"}]")
                .hooks("{\"updated\":true}")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(workflowDefinitionService.update(eq(id), any(CreateWorkflowDefinitionRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/workflow-definitions/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Workflow"))
                .andExpect(jsonPath("$.data.states").value("[\"NEW_STATE\"]"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_delete_when_admin() throws Exception {
        UUID id = UUID.randomUUID();

        doNothing().when(workflowDefinitionService).delete(id);

        mockMvc.perform(delete("/api/workflow-definitions/{id}", id)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(workflowDefinitionService).delete(id);
    }

    @Test
    void should_activate_when_authorized() throws Exception {
        UUID id = UUID.randomUUID();

        doNothing().when(workflowDefinitionService).activate(id);

        mockMvc.perform(post("/api/workflow-definitions/{id}/activate", id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("squadron-admin")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_squadron-admin"))))
                .andExpect(status().isNoContent());

        verify(workflowDefinitionService).activate(id);
    }

    @Test
    void should_deactivate_when_authorized() throws Exception {
        UUID id = UUID.randomUUID();

        doNothing().when(workflowDefinitionService).deactivate(id);

        mockMvc.perform(post("/api/workflow-definitions/{id}/deactivate", id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                .claim("roles", List.of("team-lead")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_team-lead"))))
                .andExpect(status().isNoContent());

        verify(workflowDefinitionService).deactivate(id);
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/workflow-definitions/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToCreate() throws Exception {
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(UUID.randomUUID())
                .name("Test")
                .states("[]")
                .transitions("[]")
                .build();

        mockMvc.perform(post("/api/workflow-definitions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToDelete() throws Exception {
        mockMvc.perform(delete("/api/workflow-definitions/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToDelete() throws Exception {
        mockMvc.perform(delete("/api/workflow-definitions/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_return403_when_teamLeadTriesToDelete() throws Exception {
        mockMvc.perform(delete("/api/workflow-definitions/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
