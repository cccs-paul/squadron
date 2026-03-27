package com.squadron.notification.config;

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
@ContextConfiguration(classes = {SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void should_createSecurityConfig_when_instantiated() {
        // SecurityConfig loads successfully in the Spring context
        // with MockBean JwtDecoder overriding the real one
    }

    @Test
    void should_permitActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_permitWebSocket() throws Exception {
        mockMvc.perform(get("/ws/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_requireAuth_forApiEndpoints() throws Exception {
        mockMvc.perform(get("/api/notifications/user/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
