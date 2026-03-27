package com.squadron.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.platform.config.SecurityConfig;
import com.squadron.platform.dto.ConnectionInfoResponse;
import com.squadron.platform.dto.LinkAccountRequest;
import com.squadron.platform.dto.OAuth2AuthorizeUrlResponse;
import com.squadron.platform.dto.OAuth2CallbackRequest;
import com.squadron.platform.dto.OAuth2LinkRequest;
import com.squadron.platform.dto.PatLinkRequest;
import com.squadron.platform.entity.UserPlatformToken;
import com.squadron.platform.service.UserTokenService;
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

@WebMvcTest(controllers = UserTokenController.class)
@ContextConfiguration(classes = {UserTokenController.class, SecurityConfig.class})
class UserTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserTokenService tokenService;

    @MockBean
    private JwtDecoder jwtDecoder;

    // --- POST /api/platforms/tokens/oauth2/link ---

    @Test
    @WithMockUser
    void should_linkOAuth2Account_when_validRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("auth-code-123")
                .redirectUri("https://app.example.com/callback")
                .build();

        UserPlatformToken savedToken = UserPlatformToken.builder()
                .id(tokenId)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-at")
                .refreshToken("encrypted-rt")
                .tokenType("oauth2")
                .scopes("read write")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tokenService.linkOAuth2Account(userId, connectionId, "auth-code-123", "https://app.example.com/callback"))
                .thenReturn(savedToken);

        mockMvc.perform(post("/api/platforms/tokens/oauth2/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.tokenType").value("oauth2"))
                .andExpect(jsonPath("$.data.hasRefreshToken").value(true));
    }

    @Test
    void should_return401_when_linkOAuth2Unauthenticated() throws Exception {
        OAuth2LinkRequest request = OAuth2LinkRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .authorizationCode("code")
                .redirectUri("https://app.example.com/callback")
                .build();

        mockMvc.perform(post("/api/platforms/tokens/oauth2/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/platforms/tokens/pat/link ---

    @Test
    @WithMockUser
    void should_linkPatAccount_when_validRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        PatLinkRequest request = PatLinkRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("ghp_abc123")
                .build();

        UserPlatformToken savedToken = UserPlatformToken.builder()
                .id(tokenId)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-pat")
                .tokenType("pat")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tokenService.linkPatAccount(userId, connectionId, "ghp_abc123"))
                .thenReturn(savedToken);

        mockMvc.perform(post("/api/platforms/tokens/pat/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data.tokenType").value("pat"))
                .andExpect(jsonPath("$.data.hasRefreshToken").value(false));
    }

    // --- POST /api/platforms/tokens/link ---

    @Test
    @WithMockUser
    void should_linkAccount_when_validRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        LinkAccountRequest request = LinkAccountRequest.builder()
                .connectionId(connectionId)
                .authorizationCode("legacy-code")
                .redirectUri("https://app.example.com/callback")
                .build();

        UserPlatformToken savedToken = UserPlatformToken.builder()
                .id(tokenId)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-at")
                .refreshToken("encrypted-rt")
                .tokenType("oauth2")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tokenService.linkAccount(eq(userId), any(LinkAccountRequest.class))).thenReturn(savedToken);

        mockMvc.perform(post("/api/platforms/tokens/link")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data.hasRefreshToken").value(true));

        verify(tokenService).linkAccount(eq(userId), any(LinkAccountRequest.class));
    }

    // --- GET /api/platforms/tokens/user/{userId} ---

    @Test
    @WithMockUser
    void should_listByUser_when_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();

        UserPlatformToken token1 = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(UUID.randomUUID())
                .accessToken("enc-at-1")
                .tokenType("oauth2")
                .scopes("read")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        UserPlatformToken token2 = UserPlatformToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .connectionId(UUID.randomUUID())
                .accessToken("enc-at-2")
                .tokenType("pat")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tokenService.getTokensByUser(userId)).thenReturn(List.of(token1, token2));

        mockMvc.perform(get("/api/platforms/tokens/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].tokenType").value("oauth2"))
                .andExpect(jsonPath("$.data[1].tokenType").value("pat"));
    }

    @Test
    @WithMockUser
    void should_returnEmptyList_when_noTokensForUser() throws Exception {
        UUID userId = UUID.randomUUID();

        when(tokenService.getTokensByUser(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/platforms/tokens/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void should_return401_when_listByUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/tokens/user/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/platforms/tokens/user/{userId}/connection/{connectionId} ---

    @Test
    @WithMockUser
    void should_unlinkAccount_when_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        doNothing().when(tokenService).unlinkAccount(userId, connectionId);

        mockMvc.perform(delete("/api/platforms/tokens/user/{userId}/connection/{connectionId}",
                        userId, connectionId))
                .andExpect(status().isNoContent());

        verify(tokenService).unlinkAccount(userId, connectionId);
    }

    @Test
    void should_return401_when_unlinkUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/platforms/tokens/user/{userId}/connection/{connectionId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/tokens/connections/{tenantId} ---

    @Test
    @WithMockUser
    void should_getAvailableConnections_when_authenticated() throws Exception {
        UUID tenantId = UUID.randomUUID();

        ConnectionInfoResponse conn1 = ConnectionInfoResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformType("GITHUB")
                .baseUrl("https://api.github.com")
                .authType("oauth2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        ConnectionInfoResponse conn2 = ConnectionInfoResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .platformType("JIRA")
                .baseUrl("https://myorg.atlassian.net")
                .authType("oauth2")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        when(tokenService.getAvailableConnections(tenantId)).thenReturn(List.of(conn1, conn2));

        mockMvc.perform(get("/api/platforms/tokens/connections/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].platformType").value("GITHUB"))
                .andExpect(jsonPath("$.data[1].platformType").value("JIRA"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void should_return401_when_getConnectionsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/tokens/connections/{tenantId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/platforms/tokens/oauth2/authorize/{connectionId} ---

    @Test
    @WithMockUser
    void should_generateOAuth2AuthorizeUrl_when_authenticated() throws Exception {
        UUID connectionId = UUID.randomUUID();

        OAuth2AuthorizeUrlResponse authorizeResponse = OAuth2AuthorizeUrlResponse.builder()
                .authorizeUrl("https://github.com/login/oauth/authorize?response_type=code&client_id=abc&state=xyz")
                .state("xyz-state-token")
                .connectionId(connectionId)
                .platformType("GITHUB")
                .build();

        when(tokenService.generateOAuth2AuthorizeUrl(connectionId)).thenReturn(authorizeResponse);

        mockMvc.perform(get("/api/platforms/tokens/oauth2/authorize/{connectionId}", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizeUrl").value(
                        "https://github.com/login/oauth/authorize?response_type=code&client_id=abc&state=xyz"))
                .andExpect(jsonPath("$.data.state").value("xyz-state-token"))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.platformType").value("GITHUB"));
    }

    @Test
    void should_return401_when_generateAuthorizeUrlUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/platforms/tokens/oauth2/authorize/{connectionId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/platforms/tokens/oauth2/callback ---

    @Test
    @WithMockUser
    void should_handleOAuth2Callback_when_validRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        OAuth2CallbackRequest request = OAuth2CallbackRequest.builder()
                .userId(userId)
                .connectionId(connectionId)
                .authorizationCode("auth-code-from-callback")
                .redirectUri("https://app.example.com/callback")
                .state("valid-state-token")
                .build();

        UserPlatformToken savedToken = UserPlatformToken.builder()
                .id(tokenId)
                .userId(userId)
                .connectionId(connectionId)
                .accessToken("encrypted-at")
                .refreshToken("encrypted-rt")
                .tokenType("oauth2")
                .scopes("repo user")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(tokenService.linkOAuth2AccountWithState(userId, connectionId,
                "auth-code-from-callback", "https://app.example.com/callback", "valid-state-token"))
                .thenReturn(savedToken);

        mockMvc.perform(post("/api/platforms/tokens/oauth2/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.tokenType").value("oauth2"))
                .andExpect(jsonPath("$.data.hasRefreshToken").value(true));

        verify(tokenService).linkOAuth2AccountWithState(userId, connectionId,
                "auth-code-from-callback", "https://app.example.com/callback", "valid-state-token");
    }

    @Test
    void should_return401_when_callbackUnauthenticated() throws Exception {
        OAuth2CallbackRequest request = OAuth2CallbackRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .authorizationCode("code")
                .redirectUri("https://app.example.com/callback")
                .state("some-state")
                .build();

        mockMvc.perform(post("/api/platforms/tokens/oauth2/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
