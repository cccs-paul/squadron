package com.squadron.workspace.config;

import com.squadron.workspace.client.ResilientPlatformServiceClient;
import com.squadron.workspace.controller.WorkspaceController;
import com.squadron.workspace.service.WorkspaceGitService;
import com.squadron.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, WorkspaceController.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private WorkspaceGitService workspaceGitService;

    @MockBean
    private ResilientPlatformServiceClient resilientPlatformServiceClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_permitActuatorHealth() throws Exception {
        // Actuator endpoints are not registered in @WebMvcTest, so we get 404.
        // The key assertion is that security does NOT block access (not 401/403).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_permitActuatorInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_permitSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_requireAuthForApiEndpoints() throws Exception {
        mockMvc.perform(get("/api/workspaces/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
