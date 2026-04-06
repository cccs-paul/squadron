package com.squadron.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.service.CredentialResolutionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CredentialController.class)
@ContextConfiguration(classes = {CredentialController.class, SecurityConfig.class})
class CredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CredentialResolutionService credentialResolutionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // --- POST /api/platforms/credentials/resolve ---

    @Test
    @WithMockUser(roles = "developer")
    void should_resolveCredentials_when_validRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.GIT_CLONE)
                .build();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("decrypted-token")
                .credentialType(CredentialType.OAUTH2)
                .expiresAt(Instant.now().plusSeconds(3600))
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        when(credentialResolutionService.resolveCredentials(
                eq(userId), eq(connectionId), eq(CredentialPurpose.GIT_CLONE)))
                .thenReturn(result);

        mockMvc.perform(post("/api/platforms/credentials/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("decrypted-token"))
                .andExpect(jsonPath("$.data.credentialType").value("OAUTH2"))
                .andExpect(jsonPath("$.data.gitAuthMode").value("HTTPS_TOKEN"));
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.GIT_CLONE)
                .build();

        mockMvc.perform(post("/api/platforms/credentials/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "squadron-admin")
    void should_resolveCredentials_when_adminRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.PLATFORM_API)
                .build();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("admin-token")
                .credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        when(credentialResolutionService.resolveCredentials(
                eq(userId), eq(connectionId), eq(CredentialPurpose.PLATFORM_API)))
                .thenReturn(result);

        mockMvc.perform(post("/api/platforms/credentials/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("admin-token"))
                .andExpect(jsonPath("$.data.credentialType").value("PAT"));
    }

    @Test
    @WithMockUser(roles = "team-lead")
    void should_resolveCredentials_when_teamLeadRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .purpose(CredentialPurpose.FULL)
                .build();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("tl-token")
                .sshPrivateKey("ssh-key")
                .credentialType(CredentialType.DEPLOY_KEY)
                .gitAuthMode(GitAuthMode.SSH_KEY)
                .build();

        when(credentialResolutionService.resolveCredentials(
                eq(userId), eq(connectionId), eq(CredentialPurpose.FULL)))
                .thenReturn(result);

        mockMvc.perform(post("/api/platforms/credentials/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sshPrivateKey").value("ssh-key"))
                .andExpect(jsonPath("$.data.credentialType").value("DEPLOY_KEY"))
                .andExpect(jsonPath("$.data.gitAuthMode").value("SSH_KEY"));
    }

    @Test
    @WithMockUser(roles = "viewer")
    void should_return403_when_insufficientRole() throws Exception {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.GIT_CLONE)
                .build();

        mockMvc.perform(post("/api/platforms/credentials/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
