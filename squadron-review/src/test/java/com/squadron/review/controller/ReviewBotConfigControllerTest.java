package com.squadron.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.review.dto.CreateReviewBotConfigRequest;
import com.squadron.review.dto.ReviewBotConfigDto;
import com.squadron.review.service.ReviewBotConfigService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewBotConfigController.class)
@ContextConfiguration(classes = {ReviewBotConfigController.class, com.squadron.review.config.SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class ReviewBotConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewBotConfigService botConfigService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_createBotConfig_when_validRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .botAccessToken("plain-token")
                .enabled(true)
                .autoAssign(true)
                .build();

        ReviewBotConfigDto responseDto = ReviewBotConfigDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .enabled(true)
                .autoAssign(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(botConfigService.createBotConfig(any(CreateReviewBotConfigRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/reviews/bot-config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.botUsername").value("squadron-bot"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getBotConfig_when_exists() throws Exception {
        UUID configId = UUID.randomUUID();

        ReviewBotConfigDto dto = ReviewBotConfigDto.builder()
                .id(configId)
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("squadron-bot")
                .enabled(true)
                .autoAssign(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(botConfigService.getBotConfig(configId)).thenReturn(dto);

        mockMvc.perform(get("/api/reviews/bot-config/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(configId.toString()))
                .andExpect(jsonPath("$.data.botUsername").value("squadron-bot"));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_listBotConfigs_when_tenantHasConfigs() throws Exception {
        UUID tenantId = UUID.randomUUID();

        ReviewBotConfigDto dto = ReviewBotConfigDto.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .connectionId(UUID.randomUUID())
                .botUsername("bot1")
                .enabled(true)
                .autoAssign(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(botConfigService.listBotConfigsByTenant(tenantId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reviews/bot-config/tenant/{tenantId}", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].botUsername").value("bot1"));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_updateBotConfig_when_validRequest() throws Exception {
        UUID configId = UUID.randomUUID();

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("updated-bot")
                .botAccessToken("new-token")
                .enabled(false)
                .autoAssign(false)
                .build();

        ReviewBotConfigDto responseDto = ReviewBotConfigDto.builder()
                .id(configId)
                .tenantId(request.getTenantId())
                .connectionId(request.getConnectionId())
                .botUsername("updated-bot")
                .enabled(false)
                .autoAssign(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(botConfigService.updateBotConfig(eq(configId), any(CreateReviewBotConfigRequest.class)))
                .thenReturn(responseDto);

        mockMvc.perform(put("/api/reviews/bot-config/{id}", configId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.botUsername").value("updated-bot"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_deleteBotConfig_when_exists() throws Exception {
        UUID configId = UUID.randomUUID();

        doNothing().when(botConfigService).deleteBotConfig(configId);

        mockMvc.perform(delete("/api/reviews/bot-config/{id}", configId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(botConfigService).deleteBotConfig(configId);
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews/bot-config/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_getBotToken_when_exists() throws Exception {
        UUID configId = UUID.randomUUID();

        when(botConfigService.getDecryptedBotToken(configId)).thenReturn("decrypted-token-123");

        mockMvc.perform(get("/api/reviews/bot-config/{id}/token", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("decrypted-token-123"));

        verify(botConfigService).getDecryptedBotToken(configId);
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_insufficientRole() throws Exception {
        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        mockMvc.perform(post("/api/reviews/bot-config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
