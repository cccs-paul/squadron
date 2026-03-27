package com.squadron.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.config.SecurityConfig;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.service.ProjectService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectController.class)
@ContextConfiguration(classes = {ProjectController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_createProject_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .build();

        Project savedProject = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Test Project")
                .defaultBranch("main")
                .branchStrategy("TRUNK_BASED")
                .build();

        when(projectService.createProject(any(CreateProjectRequest.class))).thenReturn(savedProject);

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Project"));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_createProject_when_teamLeadRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Lead Project")
                .build();

        Project savedProject = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Lead Project")
                .build();

        when(projectService.createProject(any(CreateProjectRequest.class))).thenReturn(savedProject);

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_listByTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).tenantId(tenantId).name("P1").build()
        );

        when(projectService.listProjectsByTenant(tenantId)).thenReturn(projects);

        mockMvc.perform(get("/api/projects/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("P1"));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_listByTeam() throws Exception {
        UUID teamId = UUID.randomUUID();
        List<Project> projects = List.of(
                Project.builder().id(UUID.randomUUID()).teamId(teamId).name("Team P").build()
        );

        when(projectService.listProjectsByTeam(teamId)).thenReturn(projects);

        mockMvc.perform(get("/api/projects/team/{teamId}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Team P"));
    }

    @Test
    @WithMockUser(roles = {"qa"})
    void should_getProject_when_exists() throws Exception {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Found Project")
                .build();

        when(projectService.getProject(projectId)).thenReturn(project);

        mockMvc.perform(get("/api/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Found Project"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_updateProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Updated Project")
                .build();

        Project updated = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Updated Project")
                .build();

        when(projectService.updateProject(eq(projectId), any(CreateProjectRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Project"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_deleteProject() throws Exception {
        UUID projectId = UUID.randomUUID();

        doNothing().when(projectService).deleteProject(projectId);

        mockMvc.perform(delete("/api/projects/{id}", projectId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).deleteProject(projectId);
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/projects/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_viewerTriesToCreate() throws Exception {
        CreateProjectRequest request = CreateProjectRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(UUID.randomUUID())
                .name("Test")
                .build();

        mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_return403_when_developerTriesToDelete() throws Exception {
        mockMvc.perform(delete("/api/projects/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
