package com.squadron.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class})
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/api/auth/jwks"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_permitActuatorHealth_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound()); // 404 since no controller, but NOT 401
    }

    @Test
    void should_permitSwaggerUi_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound()); // 404 since no controller, but NOT 401
    }

    @Test
    void should_permitWebhooks_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/webhooks/jira"))
                .andExpect(status().isNotFound()); // 404 since no controller, but NOT 401
    }

    @Test
    void should_requireAuthentication_forProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/platforms/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void should_allowAccess_when_authenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/connections"))
                .andExpect(status().isNotFound()); // 404 since no controller, but NOT 401/403
    }
}
